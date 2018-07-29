package jsmith.nknclient.client;

import com.darkyen.dave.WebbException;
import jsmith.nknclient.utils.HttpApi;
import jsmith.nknclient.utils.WsApi;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.function.BiConsumer;


/**
 *
 */
public class NKNClient {

    private static final Logger LOG = LoggerFactory.getLogger(NKNClient.class);


    private InetSocketAddress routingNodesRpc[] = null;
    private InetSocketAddress routingNodeRpc = null;
    private InetSocketAddress directNodeWS = null;
    private WsApi ws = null;

    private final Identity identity;

    private int retries = Const.RETRIES;

    private boolean running = false;

    public NKNClient(Identity identity) {
        this(identity, Const.BOOTSTRAP_NODES_RPC);
    }

    public NKNClient(Identity identity, InetSocketAddress bootstrapNodesRPC[]) {
        this.identity = identity;
        this.routingNodesRpc = bootstrapNodesRPC;
    }
    public NKNClient start() {
        if (running) throw new NKNClientError("Client is already running, cannot start again");

        boolean success = false;

        // Choose one node using round robin
        int bootstrapNodeIdx = (int)(Math.random() * routingNodesRpc.length);
        routingNodeRpc = routingNodesRpc[bootstrapNodeIdx];

        while (retries >= 0) {
            if (!routingNode(routingNodeRpc) || !establishWsConnection()) {
                retries --;
                if (retries >= 0) {
                    bootstrapNodeIdx ++;
                    if (bootstrapNodeIdx >= routingNodesRpc.length) bootstrapNodeIdx -= routingNodesRpc.length;
                    routingNodeRpc = routingNodesRpc[bootstrapNodeIdx];
                }
            } else {
                success = true;
                break;
            }
        }

        if (!success) throw new NKNClientError("Failed to connect to network");
        running = true;
        return this;
    }

    public NKNClient close() {
        if (!running) throw new NKNClientError("Client is not (yet) running, cannot close");

        try {
            ws.closeBlocking();
        } catch (InterruptedException ignored) {}
        running = false;

        return this;
    }

    private final Object lock = new Object();
    private boolean routingNode(InetSocketAddress routingNode) {
        try {


            final JSONObject parameters = new JSONObject();
            parameters.put("address", identity.getFullIdentifier());

            LOG.debug("Client is connecting to routingNode node:", routingNode);

            final String wsAddr = HttpApi.rpcCall(routingNode, "getwsaddr", parameters);

            try {
                final String[] parts = wsAddr.split(":");
                directNodeWS = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                LOG.error("Failed to reconstruct node address from string '" + wsAddr + "'");
                throw new NKNClientError("Could not initialize connection. Caused by illegal format format of ws address");
            }

            return true;
        } catch (WebbException e) {
            LOG.warn("RPC Request failed, remaining retries: {}", retries);
            return false;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean establishWsConnection() {
        LOG.debug("Client is connecting to node ws:", directNodeWS);
        final boolean success[] = {true};
        ws = new WsApi(directNodeWS);

        ws.setMessageListener( json -> {
            switch (json.get("Action").toString()) {
                case "setClient": {
                    if (json.has("Error") && (int)json.get("Error") != 0) {
                        LOG.warn("WS connection failed, remaining retries: {}", retries);
                        try {
                            ws.closeBlocking();
                        } catch (InterruptedException ignore) {}
                        success[0] = false;
                        synchronized (lock) {
                            lock.notify();
                        }
                    } else {
                        synchronized (lock) {
                            lock.notify();
                        }
                    }
                    break;
                }
                case "updateSigChainBlockHash": {
                    // TODO // if ((int)json.get("Error") == 0) onMessageUpdateSigChainBlockHash(json.get("Result").toString());
                    break;
                }
                case "receivePacket": {
                    if (!json.has("Error") || (int)json.get("Error") == 0) {
                        if (onSimpleMessageL != null) onSimpleMessageL.accept(json.get("Src").toString(), json.get("Payload").toString());
                    } else {
                        LOG.warn("Error when receiving packet: {}", json.toString());
                    }
                    break;
                }
                case "sendPacket": {
                    // TODO sent message confirmation and stuff
                    break;
                }
                default:
                    LOG.warn("Got unknown message (action={}), ignoring", json.get("Action").toString());
            }
        });

        ws.setOpenListener( ignored -> {
                    final JSONObject setClientReq = new JSONObject();
                    setClientReq.put("Action", "setClient");
                    setClientReq.put("Addr", identity.getFullIdentifier());

                    ws.send(setClientReq);
                }
        );
        ws.connect();

        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ignored) {}
        }
        return success[0];
    }

    private BiConsumer<String, String> onSimpleMessageL = null;
    public NKNClient onSimpleMessage(BiConsumer<String, String> listener) {
        onSimpleMessageL = listener;
        return this;
    }

    public void sendSimpleMessage(String destinationFullIdentifier, String message) {
        if (!running) throw new NKNClientError("Client is not running, cannot send message");

        final JSONObject messageJson = new JSONObject();
        messageJson.put("Action", "sendPacket");
        messageJson.put("Dest", destinationFullIdentifier);
        messageJson.put("Payload", message);
        messageJson.put("Signature", identity.singStringAsString(message));

        ws.send(messageJson);
    }

}
