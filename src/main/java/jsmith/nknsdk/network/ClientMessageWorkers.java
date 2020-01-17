package jsmith.nknsdk.network;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import jsmith.nknsdk.client.NKNClient;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.network.proto.MessagesP;
import jsmith.nknsdk.utils.Crypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 *
 */
public class ClientMessageWorkers {

    private static final Logger LOG = LoggerFactory.getLogger(ClientMessageWorkers.class);

    private final ClientTunnel ct;

    private final BlockingQueue<MessageJob> outboundQ = new ArrayBlockingQueue<>(100);
    private final BlockingQueue<MessageJob> timerQ = new PriorityBlockingQueue<>(100, (j1, j2) -> (int)(j1.timeoutAt - j2.timeoutAt));
    private final ConcurrentHashMap<ByteString, MessageJob> inboundQ = new ConcurrentHashMap<>();

    private final Thread outboundThread, timerThread;

    private boolean running = false;
    private ExecutorService events;

    public ClientMessageWorkers(ClientTunnel ct, int myId) {
        this.ct = ct;

        events = Executors.newFixedThreadPool(5);

        outboundThread = new Thread("OutboundMessageWorker-" + myId) {
            @Override
            public void run() {
                while(running || !outboundQ.isEmpty()) {
                    try {
                        final MessageJob job = outboundQ.take();
                        ct.messageHold.await();

                        job.timeoutAt = System.currentTimeMillis() + job.timeoutIn;
                        timerQ.put(job);
                        inboundQ.put(job.messageID, job);

                        ct.ws.sendPacket(job.payload);

                    } catch (InterruptedException ignored) {}
                }
            }
        };

        timerThread = new Thread("TimerMessageWorker-" + myId) {
            @Override
            public void run() {
                while (running || !timerQ.isEmpty() || !outboundQ.isEmpty()) {
                    try {
                        final MessageJob job = timerQ.take();
                        Thread.sleep(Math.max(job.timeoutAt - System.currentTimeMillis(), 0));

                        for (CompletableFuture<NKNClient.ReceivedMessage> p : job.promise) {
                            events.submit(() -> p.completeExceptionally(new NKNClientException.MessageAckTimeout(job.messageID)));
                        }

                        inboundQ.remove(job.messageID);

                    } catch (InterruptedException ignored) {}
                }
            }

        };

    }

    void onInboundMessage(String from, MessagesP.EncryptedMessage encryptedMessage) {
        final boolean isEncrypted = encryptedMessage.getEncrypted();

        MessagesP.Payload message;
        try {
            message = MessagesP.Payload.parseFrom(ClientEnc.decryptMessage(from, encryptedMessage, ct.identity.wallet));
        } catch (NKNClientException | InvalidProtocolBufferException e) {
            LOG.warn("Failed to decrypt message, dropping");
            return;
        }

        final MessagesP.PayloadType type = message.getType();
        final ByteString replyTo = message.getReplyToPid();
        final ByteString messageID = message.getPid();
        final ByteString data = message.getData();

        String text = null;
        try {
            if (type == MessagesP.PayloadType.TEXT) {
                text = MessagesP.TextData.parseFrom(data).getText();
            }
        } catch (InvalidProtocolBufferException e) {
            LOG.warn("Received message of TEXT type, but the content isn't valid text");
        }

        final NKNClient.ReceivedMessage receivedMessage = new NKNClient.ReceivedMessage(
            from, messageID, isEncrypted, type, type == MessagesP.PayloadType.TEXT ? text : type == MessagesP.PayloadType.BINARY ? data : null
        );

        final MessageJob job = inboundQ.get(replyTo);
        if (job != null) {
            for (int i = 0; i < job.destination.size(); i++) {
                if (job.destination.get(i).equalsIgnoreCase(from)) {
                    final CompletableFuture<NKNClient.ReceivedMessage> p = job.promise.get(i);
                    events.submit(() -> p.complete(receivedMessage));
                }
            }
        }

        if (onMessageL != null) {
            if (type != MessagesP.PayloadType.ACK) {
                events.submit(() -> {
                    Object response = onMessageL.apply(receivedMessage);
                    if (response != null) {
                        sendMessageAsync(Collections.singletonList(from), messageID, response);
                    } else if (!noAck) {
                        sendAckMessage(from, messageID);
                    }
                });
            }
        } else {
            if (type != MessagesP.PayloadType.ACK && !noAck) {
                sendAckMessage(from, messageID);
            }
        }

    }




    public void start() {
        running = true;
        timerThread.start();
        outboundThread.start();
    }

    private boolean noAck = false;
    public void setNoAutomaticACKs(boolean noAck) {
        this.noAck = noAck;
    }
    private NKNClient.EncryptionLevel encryptionLevel = NKNClient.EncryptionLevel.CONVERT_MULTICAST_TO_UNICAST_AND_ENCRYPT;
    public void setEncryptionLevel(NKNClient.EncryptionLevel level) {
        this.encryptionLevel = level;
    }

    public void close() {
        running = false;
        outboundThread.interrupt();
        timerThread.interrupt();

        try {
            outboundThread.join();
        } catch (InterruptedException ignored) {}
        try {
            timerThread.join();
        } catch (InterruptedException ignored) {}
        events.shutdown();
    }

    private Function<NKNClient.ReceivedMessage, Object> onMessageL = null;
    public void onMessage(Function<NKNClient.ReceivedMessage, Object> listener) {
        onMessageL = listener;
    }









    public List<CompletableFuture<NKNClient.ReceivedMessage>> sendMessageAsync(List<String> destination, ByteString replyTo, Object message) throws NKNClientException.UnknownObjectType {
        if (message instanceof String) {
            return sendMessageAsync(destination, replyTo, MessagesP.PayloadType.TEXT, MessagesP.TextData.newBuilder().setText((String) message).build().toByteString());
        } else if (message instanceof ByteString) {
            return sendMessageAsync(destination, replyTo, MessagesP.PayloadType.BINARY, (ByteString) message);
        } else if (message instanceof byte[]) {
            return sendMessageAsync(destination, replyTo, MessagesP.PayloadType.BINARY, ByteString.copyFrom((byte[]) message));
        } else {
            LOG.error("Cannot serialize '{}' to NKN protobuf message", message.getClass());
            throw new NKNClientException.UnknownObjectType("Cannot serialize '" + message.getClass() + "' to NKN message");
        }
    }

    public List<CompletableFuture<NKNClient.ReceivedMessage>> sendMessageAsync(List<String> destination, ByteString replyTo, MessagesP.PayloadType type, ByteString message) {
        final ByteString replyToMessageID = replyTo == null ? ByteString.copyFrom(new byte[0]) : replyTo;

        if (encryptionLevel == NKNClient.EncryptionLevel.CONVERT_MULTICAST_TO_UNICAST_AND_ENCRYPT) {

            final List<CompletableFuture<NKNClient.ReceivedMessage>> promises = new ArrayList<>();

            for (String d : destination) {
                final ByteString messageID = ByteString.copyFrom(Crypto.nextRandom4B());

                final MessagesP.Payload payload = MessagesP.Payload.newBuilder()
                        .setType(type)
                        .setPid(messageID)
                        .setReplyToPid(replyToMessageID)
                        .setData(message)
                        .setNoAck(noAck)
                        .build();

                try {
                    final ByteString encryptedPayload = ClientEnc.encryptMessage(Collections.singletonList(d), payload.toByteString(), ct.identity.wallet, NKNClient.EncryptionLevel.ENCRYPT_ONLY_UNICAST);
                    promises.addAll(sendEncryptedMessage(Collections.singletonList(d), messageID, encryptedPayload));
                } catch (NKNClientException e) {
                    LOG.warn("Failed to send message", e);
                }
            }

            return promises;

        } else {
            final ByteString messageID = ByteString.copyFrom(Crypto.nextRandom4B());

            final MessagesP.Payload payload = MessagesP.Payload.newBuilder()
                    .setType(type)
                    .setPid(messageID)
                    .setReplyToPid(replyToMessageID)
                    .setData(message)
                    .setNoAck(noAck)
                    .build();


            try {
                final ByteString encryptedPayload = ClientEnc.encryptMessage(destination, payload.toByteString(), ct.identity.wallet, encryptionLevel);

                return sendEncryptedMessage(destination, messageID, encryptedPayload);
            } catch (NKNClientException e) {
                LOG.warn("Failed to send message", e);

                return Collections.emptyList();
            }
        }
    }

    private List<CompletableFuture<NKNClient.ReceivedMessage>> sendEncryptedMessage(List<String> destination, ByteString messageID, ByteString payload) {
        if (destination.size() == 0) throw new IllegalArgumentException("At least one address is required for multicast");

        final ArrayList<CompletableFuture<NKNClient.ReceivedMessage>> promises = new ArrayList<>();
        for (String identity : destination) {
            if (identity == null || identity.isEmpty()) throw new IllegalArgumentException("Destination identity is null or empty");
            promises.add(new CompletableFuture<>());
        }

        final MessagesP.ClientMsg.Builder clientToNodeMsg = MessagesP.ClientMsg.newBuilder()
                .setPayload(payload)
                .addAllDests(destination)
                .setMaxHoldingSeconds(0);

        ClientEnc.signOutboundMessage(clientToNodeMsg, ct);

        final MessagesP.Message msg = MessagesP.Message.newBuilder()
                .setMessage(clientToNodeMsg.build().toByteString())
                .setMessageType(MessagesP.MessageType.CLIENT_MSG)
                .build();

        if (!running) throw new IllegalStateException("Client is not running, cannot send messages.");

        try {
            outboundQ.put(new MessageJob(destination, messageID, msg.toByteString(), promises, ConnectionProvider.messageAckTimeoutMS()));
        } catch (InterruptedException ignored) {}

        return promises;
    }

    private void sendAckMessage(String destination, ByteString replyTo) {
        final MessagesP.Payload payload = MessagesP.Payload.newBuilder()
                .setType(MessagesP.PayloadType.ACK)
                .setPid(ByteString.copyFrom(Crypto.nextRandom4B()))
                .setReplyToPid(replyTo)
                .setNoAck(true)
                .build();

        try {
            final ByteString encryptedPayload = ClientEnc.encryptMessage(Collections.singletonList(destination), payload.toByteString(), ct.identity.wallet, encryptionLevel);

            final MessagesP.ClientMsg.Builder clientToNodeMsg = MessagesP.ClientMsg.newBuilder()
                    .setPayload(encryptedPayload)
                    .addDests(destination)
                    .setMaxHoldingSeconds(0);

            ClientEnc.signOutboundMessage(clientToNodeMsg, ct);

            final MessagesP.Message msg = MessagesP.Message.newBuilder()
                    .setMessage(clientToNodeMsg.build().toByteString())
                    .setMessageType(MessagesP.MessageType.CLIENT_MSG)
                    .build();

            ct.ws.sendPacket(msg.toByteString());
        } catch (NKNClientException e) {
            LOG.warn("Failed to send ACK message", e);
        }
    }


    private static class MessageJob {

        private final List<String> destination;
        private final ByteString messageID, payload;
        private final List<CompletableFuture<NKNClient.ReceivedMessage>> promise;
        private final long timeoutIn;
        private long timeoutAt = -1;

        MessageJob(List<String> destination, ByteString messageID, ByteString payload, List<CompletableFuture<NKNClient.ReceivedMessage>> promise, long timeoutIn) {
            this.destination = destination;
            this.messageID = messageID;
            this.payload = payload;
            this.promise = promise;
            this.timeoutIn = timeoutIn;
        }

    }
}
