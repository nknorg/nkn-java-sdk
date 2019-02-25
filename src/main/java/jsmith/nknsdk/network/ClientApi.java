package jsmith.nknsdk.network;

import com.darkyen.dave.WebbException;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import jsmith.nknsdk.client.ErrorCodes;
import jsmith.nknsdk.client.Identity;
import jsmith.nknsdk.client.NKNClient;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.network.proto.Messages;
import jsmith.nknsdk.network.proto.Payloads;
import jsmith.nknsdk.utils.Crypto;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.CloseReason;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 *
 */
public class ClientApi extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(ClientApi.class);

    private boolean running = false;

    private InetSocketAddress directNodeWS = null;
    private WsApi ws = null;
    private AtomicInteger messageHold = new AtomicInteger(1);

    private final Identity identity;

    private static int myID = 0;
    public ClientApi(Identity identity) {
        setName("Client-" + ++myID);
        this.identity = identity;
    }


    public void startClient() throws NKNClientException {
        if (running) throw new IllegalStateException("Client is already running, cannot start again");

        reconnect();
        messageHold.decrementAndGet();

        running = true;
        setDaemon(true);
        super.start();
    }

    public void close() {
        if (!running) throw new IllegalStateException("Client is not (yet) running, cannot close");

        ws.close();
        synchronized (jobLock) {
            stop = true;
            jobLock.notify();
        }
        try {
            join();
        } catch (InterruptedException ignored) {}
    }

    private void reconnect() throws NKNClientException {
        try {
            ConnectionProvider.attempt((bootstrapNode) -> {
                if (!(bootstrapNode(bootstrapNode) && establishWsConnection())) {
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

    private boolean establishWsConnection() {
        LOG.debug("Client is connecting to node ws:", directNodeWS);
        final boolean[] success = {true};
        final Object closeLock = new Object();
        ws = new WsApi(directNodeWS);

        ws.setJsonMessageListener(json -> {
            if (json.has("Error") && json.getInt("Error") == ErrorCodes.WRONG_NODE) { // Wrong node to connect // TODO create error constants
                LOG.info("Network topology changed, re-establishing connection");

                messageHold.incrementAndGet();
                ws.close();

                try {
                    ConnectionProvider.attempt((bootstrapNode) -> {
                        final String[] parts = json.getString("Result").split(":");
                        directNodeWS = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));

                        if (!establishWsConnection()) {
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
                    crashClose = true;
                    success[0] = false;
                    close();
                }

                messageHold.decrementAndGet();
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
                        handleInboundMessage(from, payload);
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

        ws.setOpenListener( () -> {
                    final JSONObject setClientReq = new JSONObject();
                    setClientReq.put("Action", "setClient");
                    setClientReq.put("Addr", identity.getFullIdentifier());

            ws.sendPacket(setClientReq);
        });
        ws.setCLoseListener( (reason) -> {
            if (!crashClose && !stop && (reason.getCloseCode() == CloseReason.CloseCodes.CLOSED_ABNORMALLY)) {
                LOG.info("Connection closed, reconnecting");
                messageHold.incrementAndGet();
                ws.close();
                try {
                    reconnect();
                    success[0] = true;
                } catch (NKNClientException e) {
                    LOG.error("Failed to reconnect to ws", e);
                    success[0] = false;
                    crashClose = true;
                    close();
                }
                messageHold.decrementAndGet();
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


    private boolean noAck = false;
    public void setNoAutomaticACKs(boolean noAck) {
        this.noAck = noAck;
    }

    private final Object jobLock = new Object();
    private final ArrayList<MessageJob> jobs = new ArrayList<>();
    private final ArrayList<MessageJob> waitingForReply = new ArrayList<>();
    private boolean stop = false;
    private boolean crashClose = false;

    @Override
    public void run() {
        while (!stop || (!jobs.isEmpty() && !crashClose)) {
            long nextWake = -1;
            synchronized (jobLock) {
                final long now = System.currentTimeMillis();
                final Iterator<MessageJob> iterator = waitingForReply.iterator();

                jobI: while (iterator.hasNext()) {
                    final MessageJob j = iterator.next();


                    for (int i = 0; i < j.receivedAck.size(); i++) {
                        if (j.receivedAck.get(i)) {
                            j.promise.get(i).complete(j.ack.get(i));

                            if (j.receivedAck.size() > 1) {

                                j.promise.remove(i);
                                j.ack.remove(i);
                                j.receivedAck.remove(i);
                                j.destination.remove(i);

                                i--;
                            } else {
                                iterator.remove();
                                continue jobI;
                            }
                        }
                    }

                    if (j.timeoutAt <= now) {
                        j.promise.forEach(p -> p.completeExceptionally(new NKNClientException.MessageAckTimeout(j.messageID)));
                        iterator.remove();
                    } else {
                        if (nextWake == -1) {
                            nextWake = j.timeoutAt;
                        } else {
                            nextWake = Math.min(nextWake, j.timeoutAt);
                        }
                    }
                }
            }

            MessageJob j = null;
            if (messageHold.get() == 0) {
                synchronized (jobLock) {
                    if (!jobs.isEmpty()) {
                        j = jobs.remove(0);
                    }
                }
            } else {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
            }
            if (j != null) {
                waitingForReply.add(j);
                ws.sendPacket(j.payload);
                if (nextWake == -1) {
                    nextWake = j.timeoutAt;
                } else {
                    nextWake = Math.min(nextWake, j.timeoutAt);
                }
            }

            synchronized (jobLock) {
                if (jobs.isEmpty()) {
                    try {
                        if (!stop) {
                            if (nextWake == -1) {
                                jobLock.wait();
                            } else {
                                jobLock.wait(Math.max(0, nextWake - System.currentTimeMillis()));
                            }
                        }
                    } catch (InterruptedException ignored) {}
                }
            }
        }

        running = false;
    }


    private Function<NKNClient.ReceivedMessage, Object> onMessageL = null;
    public void onMessage(Function<NKNClient.ReceivedMessage, Object> listener) {
        onMessageL = listener;
    }


    private void handleInboundMessage(String from, Payloads.Payload message) {
        final Payloads.PayloadType type = message.getType();
        final ByteString replyTo = message.getReplyToPid();
        final ByteString messageID = message.getPid();

        boolean isReplyTo = false;

        synchronized (jobLock) {
            for (MessageJob j : waitingForReply) {
                if (j.messageID.equals(replyTo)) {
                    final int indexOf = j.destination.indexOf(from);
                    if (type == Payloads.PayloadType.TEXT) {
                        try {
                            j.ack.set(indexOf,
                                    new NKNClient.ReceivedMessage(
                                        from,
                                        messageID,
                                        Payloads.PayloadType.TEXT,
                                        Payloads.TextData.parseFrom(message.getData()).getText()
                                ));
                        } catch (InvalidProtocolBufferException e) {
                            LOG.warn("Received packet is of type TEXT but does not contain valid text data");
                        }
                    } else if (type == Payloads.PayloadType.BINARY) {
                        j.ack.set(indexOf,
                                new NKNClient.ReceivedMessage(
                                    from,
                                    messageID,
                                    Payloads.PayloadType.BINARY,
                                    message.getData()
                            ));
                    } else if (type == Payloads.PayloadType.ACK) {
                        j.ack.set(indexOf,
                                new NKNClient.ReceivedMessage(
                                        from,
                                        messageID,
                                        Payloads.PayloadType.ACK,
                                        null
                                ));
                    }
                    j.receivedAck.set(indexOf, true);
                    isReplyTo = true;
                }
            }

            jobLock.notify();
        }

        Object ackMessage = null;
        if (!isReplyTo) {

            if (type == Payloads.PayloadType.TEXT) {
                try {
                    if (onMessageL != null) {
                        ackMessage = onMessageL.apply(new NKNClient.ReceivedMessage(from, messageID, type, Payloads.TextData.parseFrom(message.getData()).getText()));
                    }
                } catch (InvalidProtocolBufferException e) {
                    LOG.warn("Received packet is of type TEXT but does not contain valid text data");
                }
            } else if (type == Payloads.PayloadType.BINARY) {
                if (onMessageL != null) {
                    ackMessage = onMessageL.apply(new NKNClient.ReceivedMessage(from, messageID, type, message.getData()));
                }
            }
        }

        if (type != Payloads.PayloadType.ACK) {
            if (ackMessage == null) {
                if (!message.getNoAck()) {
                    sendAckMessage(from, messageID);
                }
            } else {
                sendMessageAsync(Collections.singletonList(from), messageID, ackMessage);
            }
        }

    }

    public List<CompletableFuture<NKNClient.ReceivedMessage>> sendMessageAsync(List<String> destination, ByteString replyTo, Object message) throws NKNClientException.UnknownObjectType {
        if (message instanceof String) {
            return sendMessageAsync(destination, replyTo, Payloads.PayloadType.TEXT, Payloads.TextData.newBuilder().setText((String) message).build().toByteString());
        } else if (message instanceof ByteString) {
            return sendMessageAsync(destination, replyTo, Payloads.PayloadType.BINARY, (ByteString) message);
        } else if (message instanceof byte[]) {
            return sendMessageAsync(destination, replyTo, Payloads.PayloadType.BINARY, ByteString.copyFrom((byte[]) message));
        } else {
            LOG.error("Cannot serialize '{}' to NKN protobuf message", message.getClass());
            throw new NKNClientException.UnknownObjectType("Cannot serialize '" + message.getClass() + "' to NKN message");
        }
    }

    public List<CompletableFuture<NKNClient.ReceivedMessage>> sendMessageAsync(List<String> destination, ByteString replyTo, Payloads.PayloadType type, ByteString message) {
        final ByteString messageID = ByteString.copyFrom(Crypto.nextRandom4B());
        final ByteString replyToMessageID = replyTo == null ? ByteString.copyFrom(new byte[0]) : replyTo;


        final Payloads.Payload payload = Payloads.Payload.newBuilder()
                .setType(type)
                .setPid(messageID)
                .setReplyToPid(replyToMessageID)
                .setData(message)
                .setNoAck(noAck)
                .build();


        return sendOutboundMessage(destination, messageID, payload.toByteString());
    }

    private List<CompletableFuture<NKNClient.ReceivedMessage>> sendOutboundMessage(List<String> destination, ByteString messageID, ByteString payload) {
        if (destination.size() == 0) throw new IllegalArgumentException("At least one address is required for multicast");

        final Messages.ClientToNodeMessage binMsg = Messages.ClientToNodeMessage.newBuilder()
                .setDest(destination.get(0))
                .setPayload(payload)
                .addAllDests(destination.subList(1, destination.size()))
                .setMaxHoldingSeconds(0)
                .build();

        final ArrayList<CompletableFuture<NKNClient.ReceivedMessage>> promises = new ArrayList<>();
        for (String ignored : destination) {
            promises.add(new CompletableFuture<>());
        }

        final MessageJob j = new MessageJob(new ArrayList<>(destination), messageID, binMsg.toByteString(), new ArrayList<>(promises), System.currentTimeMillis() + ConnectionProvider.messageAckTimeoutMS());

        LOG.debug("Queueing new MessageJob");
        synchronized (jobLock) {
            jobs.add(j);
            jobLock.notify();
        }

        return promises;
    }

    public void sendAckMessage(String destination, ByteString replyTo) {
        final Payloads.Payload payload = Payloads.Payload.newBuilder()
                .setType(Payloads.PayloadType.ACK)
                .setPid(ByteString.copyFrom(Crypto.nextRandom4B()))
                .setReplyToPid(replyTo)
                .setNoAck(true)
                .build();

        final Messages.ClientToNodeMessage binMsg = Messages.ClientToNodeMessage.newBuilder()
                .setDest(destination)
                .setPayload(payload.toByteString())
                .setMaxHoldingSeconds(0)
                .build();

        ws.sendPacket(binMsg.toByteString());
    }


    private static class MessageJob {

        private final List<String> destination;
        private final ByteString messageID, payload;
        private final List<CompletableFuture<NKNClient.ReceivedMessage>> promise;
        private final long timeoutAt;

        private final List<Boolean> receivedAck = new ArrayList<>();
        private final List<NKNClient.ReceivedMessage> ack = new ArrayList<>();

        MessageJob(List<String> destination, ByteString messageID, ByteString payload, List<CompletableFuture<NKNClient.ReceivedMessage>> promise, long timeoutAt) {
            this.destination = destination;
            this.messageID = messageID;
            this.payload = payload;
            this.promise = promise;
            this.timeoutAt = timeoutAt;

            for (String ignored : destination) {
                receivedAck.add(false);
                ack.add(null);
            }
        }

    }
}
