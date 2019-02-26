package jsmith.nknsdk.network;

import com.darkyen.dave.WebbException;
import com.google.protobuf.InvalidProtocolBufferException;
import jsmith.nknsdk.client.ErrorCodes;
import jsmith.nknsdk.client.Identity;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.network.proto.Messages;
import jsmith.nknsdk.network.proto.Payloads;
import jsmith.nknsdk.utils.CountLatch;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static javax.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE;
import static javax.websocket.CloseReason.CloseCodes.NO_STATUS_CODE;

/**
 *
 */
public class ClientTunnel {

    private static final Logger LOG = LoggerFactory.getLogger(ClientTunnel.class);

    private InetSocketAddress directNodeWS = null;
    WsApi ws = null;
    CountLatch messageHold = new CountLatch(1);

    private final Identity identity;

    private static int id = 0;
    private final int myId;
    private final ClientMessages cm;
    public ClientTunnel(Identity identity) {
        this.identity = identity;
        this.myId = ++id;
        cm = new ClientMessages(this, myId);
    }

    private boolean running = false;
    public void startClient() throws NKNClientException {
        if (running) throw new IllegalStateException("Client has already started, cannot start again");
        running = true;

        reconnect();
        messageHold.countDown();
        cm.start();
    }

    public ClientMessages getAssociatedCM() {
        return cm;
    }

    private void reconnect() throws NKNClientException {
        try {
            ConnectionProvider.attempt((bootstrapNode) -> {
                if (!bootstrapNode(bootstrapNode)) {
                    throw new NKNClientException("Couldn't contact bootstrap node");
                } else if (!setupWsConnection()) {
                    throw new NKNClientException("Connection to network refused");
                }
                return true;
            });
        } catch (Exception t) {
            if (t instanceof NKNClientException) throw (NKNClientException) t;
            throw new NKNClientException("Failed to connect to network", t);
        }
    }

    private boolean bootstrapNode(InetSocketAddress bootstrapNode) {
        try {

            final JSONObject parameters = new JSONObject();
            parameters.put("address", identity.getFullIdentifier());

            LOG.debug("Client is connecting to bootstrapNode node:", bootstrapNode);

            final String wsAddr = HttpApi.rpcCall(bootstrapNode, "getwsaddr", parameters);

            if (wsAddr != null) {
                try {
                    final String[] parts = wsAddr.split(":");
                    directNodeWS = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    LOG.error("Failed to reconstruct node address from string '{}'", wsAddr);
                    return false;
                }
            } else {
                LOG.error("Did not receive valid rpc result. Result does not contain node address");
                return false;
            }

            return true;
        } catch (WebbException e) {
            LOG.warn("RPC Request failed");
            return false;
        }
    }

    private final Object sigChainHashLock = new Object();
    private String sigChainHash = "";
    public String currentSigChainBlockHash() {
        synchronized (sigChainHashLock) {
            return sigChainHash;
        }
    }

    private AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    public boolean shouldReconnect() {
        return shouldReconnect.get();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean setupWsConnection() {
        LOG.debug("Client is connecting to node ws:", directNodeWS);
        final boolean[] success = {true};
        final Object closeLock = new Object();
        ws = new WsApi(directNodeWS);

        ws.setJsonMessageListener(json -> {
            if (json.has("Error") && json.getInt("Error") == ErrorCodes.WRONG_NODE) {
                LOG.info("Network topology changed, re-establishing connection");

                messageHold.countUp();
                ws.close();

                try {
                    ConnectionProvider.attempt((bootstrapNode) -> {
                        final String[] parts = json.getString("Result").split(":");
                        directNodeWS = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));

                        if (!setupWsConnection()) {
                            throw new NKNClientException("Connection to network refused");
                        }

                        return true;
                    });
                    success[0] = true;
                } catch (Exception t) {
                    if (t instanceof NKNClientException) {
                        LOG.error("Failed to reconnect to ws", t);
                    } else {
                        LOG.error("Failed to reconnect to ws", new NKNClientException("Failed to connect to network", t));
                    }
                    success[0] = false;
                    shouldReconnect.set(false);
                    cm.close();
                }

                messageHold.countDown();
                synchronized (closeLock) {
                    closeLock.notify();
                }

            } else {

                switch (json.getString("Action")) {
                    case "setClient": {
                        if (json.has("Error") && json.getInt("Error") != ErrorCodes.SUCCESS) {
                            LOG.warn("WS connection failed");
                            ws.close();
                            success[0] = false;
                        }
                        synchronized (closeLock) {
                            closeLock.notify();
                        }
                        break;
                    }
                    case "updateSigChainBlockHash": {
                        if (json.getInt("Error") == ErrorCodes.SUCCESS) {
                            final String newSigChainHash = json.getString("Result");
                            synchronized (sigChainHashLock) {
                                sigChainHash = newSigChainHash;
                            }

                        }
                        break;
                    }
                    default:
                        LOG.warn("Got unknown message (action='{}'), ignoring", json.getString("Action"));
                }
            }
        });

        ws.setProtobufMessageListener(bytes -> {
            try {
                final Messages.NodeToClientMessage msg = Messages.NodeToClientMessage.parseFrom(bytes);

                final String from = msg.getSrc();
                final Payloads.Payload payload = Payloads.Payload.parseFrom(msg.getPayload());

                switch (payload.getType()) {
                    case ACK:
                    case TEXT:
                    case BINARY:
                        cm.onInboundMessage(from, payload);
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

        ws.setOpenListener(() -> {
            final JSONObject setClientReq = new JSONObject();
            setClientReq.put("Action", "setClient");
            setClientReq.put("Addr", identity.getFullIdentifier());

            ws.sendPacket(setClientReq);
            System.out.println("Packet sent");
        });
        ws.setCLoseListener((reason) -> {
            if (shouldReconnect.get() && !cm.isScheduledStop() && (reason.getCloseCode() != NORMAL_CLOSURE && reason.getCloseCode() != NO_STATUS_CODE)) {
                LOG.info("Connection closed, reconnecting");
                messageHold.countUp();
                ws.close();
                try {
                    reconnect();
                    success[0] = true;
                } catch (NKNClientException e) {
                    LOG.error("Failed to reconnect to ws", e);
                    success[0] = false;
                    shouldReconnect.set(false);
                    cm.close();
                }
                messageHold.countDown();
                synchronized (closeLock) {
                    closeLock.notify();
                }
            }
        });
        ws.connect();

        synchronized (closeLock) {
            try {
                closeLock.wait();
            } catch (InterruptedException ignored) {}
        }
        return success[0];
    }
}
