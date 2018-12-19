package jsmith.nknclient.client;

import com.darkyen.dave.WebbException;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import jsmith.nknclient.Const;
import jsmith.nknclient.network.HttpApi;
import jsmith.nknclient.network.WsApi;
import jsmith.nknclient.network.proto.Messages;
import jsmith.nknclient.network.proto.Payloads;
import jsmith.nknclient.utils.Crypto;
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


    private InetSocketAddress[] routingNodesRpc = null;
    private InetSocketAddress routingNodeRpc = null;
    private InetSocketAddress directNodeWS = null;
    private WsApi ws = null;

    private final Identity identity;

    private int retries = Const.RETRIES;

    private boolean running = false;

    public NKNClient(Identity identity) {
        this(identity, Const.BOOTSTRAP_NODES_RPC);
    }

    public NKNClient(Identity identity, InetSocketAddress[] bootstrapNodesRPC) {
        this.identity = identity;
        this.routingNodesRpc = bootstrapNodesRPC;
    }
    public NKNClient start() {
        if (running) throw new NKNClientError("Client is already running, cannot start again");

        boolean success = false;

        // Choose one node using round robin
        int routingNodeIdx = (int)(Math.random() * routingNodesRpc.length);
        routingNodeRpc = routingNodesRpc[routingNodeIdx];

        while (retries >= 0) {
            if (!routingNode(routingNodeRpc) || !establishWsConnection()) {
                retries --;
                if (retries >= 0) {
                    routingNodeIdx ++;
                    if (routingNodeIdx >= routingNodesRpc.length) routingNodeIdx -= routingNodesRpc.length;
                    routingNodeRpc = routingNodesRpc[routingNodeIdx];
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

    public void close() {
        if (!running) throw new NKNClientError("Client is not (yet) running, cannot close");

        try {
            ws.closeBlocking();
        } catch (InterruptedException ignored) {}
        running = false;

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
        final boolean[] success = {true};
        ws = new WsApi(directNodeWS);

        ws.setJsonMessageListener(json -> {
            switch (json.get("Action").toString()) {
                case "setClient": {
                    if (json.has("Error") && (int)json.get("Error") != 0) {
                        LOG.warn("WS connection failed, remaining retries: {}", retries);
                        ws.close();
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
                default:
                    LOG.warn("Got unknown message (action='{}'), ignoring", json.get("Action").toString());
            }
        });

        ws.setProtobufMessageListener(bytes -> {
            try {
                final Messages.InboundMessage msg = Messages.InboundMessage.parseFrom(bytes);

                final String from = msg.getSrc();
                final Payloads.Payload payload = Payloads.Payload.parseFrom(msg.getPayload());
                switch (payload.getType()) {
                    case ACK: // TODO
                        break;
                    case TEXT:
                        if (onTextMessageL != null) {
                            onTextMessageL.accept(from, Payloads.TextData.parseFrom(payload.getData()).getText());
                        }
                        break;
                    case BINARY:
                        if (onBinaryMessageL != null) {
                            onBinaryMessageL.accept(from, payload.getData());
                        }
                        break;

                    default:
                        LOG.warn("Got invalid payload type {}, ignoring", payload.getType());
                        break;
                }

            } catch (InvalidProtocolBufferException e) {
                LOG.warn("Got invalid binary message, ignoring");
                e.printStackTrace();
            }
        });

        ws.setOpenListener( ignored -> {
                    final JSONObject setClientReq = new JSONObject();
                    setClientReq.put("Action", "setClient");
                    setClientReq.put("Addr", identity.getFullIdentifier());

                    ws.sendPacket(setClientReq);
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

    private BiConsumer<String, String> onTextMessageL = null;
    public NKNClient onTextMessage(BiConsumer<String, String> listener) {
        onTextMessageL = listener;
        return this;
    }

    private BiConsumer<String, ByteString> onBinaryMessageL = null;
    public NKNClient onBinaryMessage(BiConsumer<String, ByteString> listener) {
        onBinaryMessageL = listener;
        return this;
    }

    public void sendTextMessage(String destinationFullIdentifier, String message) {
        final Payloads.TextData td = Payloads.TextData.newBuilder()
                .setText(message)
                .build();

        sendMessage(destinationFullIdentifier, Payloads.PayloadType.TEXT, td.toByteString());
    }

    public void sendBinaryMessage(String destinationFullIdentifier, byte[] message) {
        sendBinaryMessage(destinationFullIdentifier, ByteString.copyFrom(message));
    }

    public void sendBinaryMessage(String destinationFullIdentifier, ByteString message) {
        sendMessage(destinationFullIdentifier, Payloads.PayloadType.BINARY, message);
    }

    private void sendMessage(String destination, Payloads.PayloadType type, ByteString message) {
        if (!running) throw new NKNClientError("Client is not running, cannot send message");

        final Payloads.Payload payload = Payloads.Payload.newBuilder()
                .setType(type)
                .setPid(ByteString.copyFrom(Crypto.nextRandom4B()))
                .setReplyToPid(ByteString.copyFrom(new byte[0]))
                .setData(message)
                .build();

        final Messages.OutboundMessage binMsg = Messages.OutboundMessage.newBuilder()
                .setDest(destination)
                .setPayload(payload.toByteString())
                // .addDests() // TODO multicast
                .setMaxHoldingSeconds(0)
                .build();

        ws.sendPacket(binMsg.toByteString());
    }

}
