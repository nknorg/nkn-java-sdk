package jsmith.nknsdk.network;

import com.darkyen.dave.WebbException;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import jsmith.nknsdk.client.Identity;
import jsmith.nknsdk.client.NKNClient;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.network.proto.MessagesP;
import jsmith.nknsdk.network.session.SessionHandler;
import jsmith.nknsdk.utils.CountLatch;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.Hex;
import org.java_websocket.util.NamedThreadFactory;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class ClientTunnel {

    private static final Logger LOG = LoggerFactory.getLogger(ClientTunnel.class);

    private static final ExecutorService reconnectionService = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("reconnector"));

    private InetSocketAddress directNodeWS = null;
    volatile WsApi ws = null;
    CountLatch messageHold = new CountLatch(1);

    final Identity identity;

    private static int id = 0;
    final int myId;
    private final ClientMessageWorker cm;
    final NKNClient forClient;
    public final ArrayList<ClientTunnel> multiclients = new ArrayList<>();
    private final SessionHandler handler;
    public ClientTunnel(Identity identity, NKNClient forClient) {
        this(identity, forClient, null);
    }
    ClientTunnel(Identity identity, NKNClient forClient, SessionHandler handler) {
        this.identity = identity;
        this.myId = ++id;
        this.forClient = forClient;
        this.handler = handler == null ? new SessionHandler(this, myId) : handler;
        cm = new ClientMessageWorker(this, myId, this.handler);
    }

    boolean running = false;
    public void startClient() throws NKNClientException {
        if (running) throw new IllegalStateException("Client has already started, cannot start again");
        running = true;

        reconnect();
        messageHold.countDown();
        cm.start();

        for (ClientTunnel ct : multiclients) {
            ct.startClient();
        }
    }

    private AtomicInteger multiclientPrefix = new AtomicInteger(0);
    public void ensureMulticlients(int multiclientCount) throws NKNClientException {
        LOG.debug("Ensuring {} multiclients", multiclientCount);
        // TODO create multiclients in parallel
        while (multiclients.size() < multiclientCount) {
            final String prefix = "__" + multiclientPrefix.getAndIncrement() + "__.";
            final Identity id = new Identity(prefix + identity.name, identity.wallet);
            final ClientTunnel ct = new ClientTunnel(id, forClient, handler);
            multiclients.add(ct);
            if (running) ct.startClient();
        }
        // TODO close those that we dont need anymore?
    }

    public ClientMessageWorker getAssociatedCM() {
        return cm;
    }
    public SessionHandler getAssociatedSessionHandler() {
        return handler;
    }

    private void reconnect() throws NKNClientException {
        LOG.debug("(Re)connecting...");
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

    ByteString nodePubkey, nodeId;

    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private boolean bootstrapNode(InetSocketAddress bootstrapNode) {
        try {

            final JSONObject parameters = new JSONObject();
            parameters.put("address", identity.getFullIdentifier());

            LOG.debug("Client is connecting to bootstrapNode node: {}", bootstrapNode);

            JSONObject result = HttpApi.rpcCallJson(bootstrapNode, "getwsaddr", parameters);
            if (result.has("result")) {
                result = result.getJSONObject("result");

                final String wsAddr = result.getString("addr");
                try {
                    nodePubkey = ByteString.copyFrom(Hex.decode(result.getString("pubkey")));
                    nodeId = ByteString.copyFrom(Hex.decode(result.getString("id")));
                } catch (DecoderException e) {
                    LOG.warn("Couldn't decode response, invalid node");
                    return false;
                }

                try {
                    final String[] parts = wsAddr.split(":");
                    directNodeWS = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    LOG.error("Failed to reconstruct node address from string '{}'", wsAddr);
                    return false;
                }
            } else {
                LOG.debug("getwsaddr response: {}", result.toString());
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
    private ByteString sigChainHash = null;
    public ByteString currentSigChainBlockHash() {
        synchronized (sigChainHashLock) {
            return sigChainHash;
        }
    }

    public void close() {
        this.running = false;
        for (ClientTunnel ct : multiclients) {
            ct.close();
        }
        cm.close();
        ws.close();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean setupWsConnection() {
        LOG.debug("Client is connecting to node ws: {}", directNodeWS);
        final boolean[] success = {true};
        final CountDownLatch closeLatch = new CountDownLatch(1);
        ws = new WsApi(directNodeWS);

        ws.setJsonMessageListener(json -> {
            if (json.has("Error") && json.getInt("Error") == ErrorCodes.WRONG_NODE) {
                LOG.info("Network topology changed, re-establishing connection");

                LOG.debug("WrongNode err: Message hold ({})+1", messageHold.getCount());
                messageHold.countUp();

                ws.close();

                try {
                    ConnectionProvider.attempt((bootstrapNode) -> {
                        System.out.println(json.toString());
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

                LOG.debug("WrongNode err: Message hold ({})-1", messageHold.getCount());
                messageHold.countDown();
                closeLatch.countDown();

            } else {

                switch (json.getString("Action")) {
                    case "setClient": {
                        if (json.has("Error") && json.getInt("Error") != ErrorCodes.SUCCESS) {
                            LOG.warn("WS connection failed");
                            ws.close();
                            success[0] = false;
                        }
                        final JSONObject result = json.getJSONObject("Result");
                        final JSONObject node = result.getJSONObject("node");

                        if (node.has("id")) {
                            if (!Hex.toHexString(nodeId.toByteArray()).equalsIgnoreCase(node.getString("id"))) {
                                LOG.warn("WS Node has unexpected ID. Possible MiTM attempt; Reconnecting");
                                ws.close();
                                success[0] = false;
                            }
                            if (!Hex.toHexString(nodePubkey.toByteArray()).equalsIgnoreCase(node.getString("pubkey"))) {
                                LOG.warn("WS Node has unexpected pubkey. Possible MiTM attempt; Reconnecting");
                                ws.close();
                                success[0] = false;
                            }
                        }

                        if (result.has("sigChainBlockHash")) {
                            try {
                                final ByteString newSigChainHash = ByteString.copyFrom(Hex.decode(result.getString("sigChainBlockHash")));
                                synchronized (sigChainHashLock) {
                                    sigChainHash = newSigChainHash;
                                }
                            } catch (DecoderException e) {
                                LOG.warn("Failed to decode sigChainBlockHash: {}", result.getString("sigChainBlockHash"));
                                sigChainHash = null;
                            }
                        }

                        closeLatch.countDown();
                        break;
                    }
                    case "updateSigChainBlockHash": {
                        if (json.getInt("Error") == ErrorCodes.SUCCESS) {
                            try {
                                final ByteString newSigChainHash = ByteString.copyFrom(Hex.decode(json.getString("Result")));
                                synchronized (sigChainHashLock) {
                                    sigChainHash = newSigChainHash;
                                }
                            } catch (DecoderException e) {
                                LOG.warn("Failed to decode sigChainBlockHash: {}", json.getString("Result"));
                                sigChainHash = null;
                            }

                        }
                        break;
                    }
                    case "sendRawBlock": {
                        // Ignore for now. TODO maybe use sendRawBlock message somehow
                        break;
                    }
                    default:
                        LOG.warn("Got unknown message (action='{}'), ignoring", json.getString("Action"));
                }
            }
        });

        ws.setProtobufMessageListener(bytes -> {
            try {
                if (running) {
                    final MessagesP.Message msg = MessagesP.Message.parseFrom(bytes);
                    if (msg.getMessageType() == MessagesP.MessageType.NODE_MSG) {
                        final MessagesP.NodeMsg nodeToClientMsg = MessagesP.NodeMsg.parseFrom(msg.getMessage());

                        final String from = nodeToClientMsg.getSrc();
                        final MessagesP.EncryptedMessage pldMsg = MessagesP.EncryptedMessage.parseFrom(nodeToClientMsg.getPayload());

                        final ByteString prevSig = nodeToClientMsg.getPrevSignature();
                        if (prevSig != null && prevSig.size() != 0) {
                            ByteString receiptPayload = ClientEnc.generateNewReceipt(prevSig, this);
                            final ByteString receiptMsg = MessagesP.Message.newBuilder()
                                    .setMessage(receiptPayload)
                                    .setMessageType(MessagesP.MessageType.RECEIPT_MSG)
                                    .build().toByteString();
                            ws.sendPacket(receiptMsg);
                        }
                        cm.onInboundMessage(from, pldMsg);
                    } else {
                        LOG.warn("Received unsupported message type, ignoring ({})", msg.getMessageType());
                    }
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
        });
        ws.setCLoseListener((reason) -> {
            if (shouldReconnect.get() && running) {
                LOG.info("Connection closed, reconnecting");
                LOG.debug("Ws on close: Message hold ({})+1", messageHold.getCount());
                messageHold.countUp();
                ws.close();
                reconnectionService.submit(() -> {
                    try {
                        reconnect();
                        success[0] = true;
                    } catch (NKNClientException e) {
                        LOG.error("Failed to reconnect to ws", e);
                        success[0] = false;
                        shouldReconnect.set(false);
                        cm.close();
                    }
                    LOG.debug("Ws on close: Message hold ({})-1", messageHold.getCount());
                    messageHold.countDown();
                    closeLatch.countDown();
                });
            }
        });
        ws.connect();

        try {
            closeLatch.await();
        } catch (InterruptedException ignored) {
            return false;
        }
        return success[0];
    }
}
