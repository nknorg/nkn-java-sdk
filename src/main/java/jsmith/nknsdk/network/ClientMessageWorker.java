package jsmith.nknsdk.network;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import jsmith.nknsdk.client.NKNClient;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.client.SimpleMessagesProtocol;
import jsmith.nknsdk.network.proto.MessagesP;
import jsmith.nknsdk.network.session.SessionHandler;
import jsmith.nknsdk.utils.Crypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

/**
 *
 */
public class ClientMessageWorker {

    private static final Logger LOG = LoggerFactory.getLogger(ClientMessageWorker.class);

    public static final int MAX_CONNECTION_WINSIZE = 256;
    public static final int DEFAULT_INITIAL_CONNECTION_WINSIZE = 16;
    public static final int MIN_CONNECTION_WINSIZE = 1;
    public static final int INITIAL_RTO = ConnectionProvider.messageAckTimeoutMS();

    private final ClientTunnel ct;

    private final BlockingQueue<MessageJob> outboundQ = new ArrayBlockingQueue<>(16);
    private final BlockingQueue<MessageJob> timerQ = new PriorityBlockingQueue<>(100, (j1, j2) -> (int)(j1.timeoutAt - j2.timeoutAt));
    private final ConcurrentHashMap<ByteString, MessageJob> inboundQ = new ConcurrentHashMap<>();

    private final Thread outboundThread, timerThread;

    private boolean running = false;
    private ExecutorService events;

    private final SessionHandler sessionHandler;

    public ClientMessageWorker(ClientTunnel ct, int myId, SessionHandler sessionHandler) {
        this.ct = ct;
        this.sessionHandler = sessionHandler;

        events = Executors.newFixedThreadPool(5);

        outboundThread = new Thread("OutboundMessageWorker-" + myId) {
            @Override
            public void run() {
                while(running || !outboundQ.isEmpty()) {
                    try {
                        final MessageJob job = outboundQ.take();
                        ct.messageHold.await();

                        job.timeoutAt = System.currentTimeMillis() + job.timeoutIn;
                        if (!job.noreplyQ) {
                            timerQ.put(job);
                            inboundQ.put(job.messageID, job);
                        }

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

                        for (CompletableFuture<SimpleMessagesProtocol.ReceivedMessage> p : job.promise) {
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
        if (!isEncrypted) {
            if (ct.forClient.getPeerEncryptionRequirement() == NKNClient.PeerEncryptionRequirement.ON_NON_ENCRYPTED_MESSAGE___ALLOW_NONE_DROP_ALL) return;
        }

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

        if (!isEncrypted && type != MessagesP.PayloadType.ACK) {
            if (ct.forClient.getPeerEncryptionRequirement() == NKNClient.PeerEncryptionRequirement.ON_NON_ENCRYPTED_MESSAGE___ALLOW_ACK_DROP_OTHER) return;
        }

        String text = null;
        try {
            if (type == MessagesP.PayloadType.TEXT) {
                text = MessagesP.TextData.parseFrom(data).getText();
            }
        } catch (InvalidProtocolBufferException e) {
            LOG.warn("Received message of TEXT type, but the content isn't valid text");
        }

        final SimpleMessagesProtocol.ReceivedMessage receivedMessage = new SimpleMessagesProtocol.ReceivedMessage(
            from, messageID, isEncrypted, type, type == MessagesP.PayloadType.TEXT ? text : type == MessagesP.PayloadType.BINARY ? data : null
        );

        if (type == MessagesP.PayloadType.SESSION) {
            sessionHandler.onMessage(this, from, messageID, data);
        } else {

            final MessageJob job = inboundQ.get(replyTo);
            if (job != null) {
                for (int i = 0; i < job.destination.size(); i++) {
                    if (job.destination.get(i).equalsIgnoreCase(from)) {
                        final CompletableFuture<SimpleMessagesProtocol.ReceivedMessage> p = job.promise.get(i);
                        events.submit(() -> p.complete(receivedMessage));
                    }
                }
            }

            if (ct.forClient.simpleMessagesProtocol().getOnMessageListener() != null) {
                if (type != MessagesP.PayloadType.ACK) {
                    events.submit(() -> {
                        Object response = ct.forClient.simpleMessagesProtocol().getOnMessageListener().apply(receivedMessage);
                        if (response != null) {
                            sendMessageAsync(Collections.singletonList(from), messageID, response);
                        } else if (!ct.forClient.simpleMessagesProtocol().isNoAutomaticACKs()) {
                            sendAckMessage(from, messageID);
                        }
                    });
                }
            } else {
                if (type != MessagesP.PayloadType.ACK && !ct.forClient.simpleMessagesProtocol().isNoAutomaticACKs()) {
                    sendAckMessage(from, messageID);
                }
            }
        }

    }




    public void start() {
        running = true;
        timerThread.start();
        outboundThread.start();
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



    private final Object winSizeLock = new Object();
    private final HashMap<String, Integer> maxWinSize = new HashMap<>();
    private final HashMap<String, Integer> usedWinSize = new HashMap<>();
    private final HashMap<String, Integer> trackedRto = new HashMap<>();
    private final ConcurrentHashMap<String, Object> trackedLock = new ConcurrentHashMap<>();

    public void trackWinSize(String remote, int initialMaxWinSize) {
        synchronized (winSizeLock) {
            maxWinSize.putIfAbsent(remote, Math.min(MAX_CONNECTION_WINSIZE, Math.max(MIN_CONNECTION_WINSIZE, initialMaxWinSize)));
            usedWinSize.putIfAbsent(remote, 0);
            trackedRto.putIfAbsent(remote, INITIAL_RTO);
            trackedLock.putIfAbsent(remote, new Object());
        }
    }
    public boolean isWinSizeAvailable(String remote) {
        synchronized (winSizeLock) {
            int max = maxWinSize.getOrDefault(remote, -1);
            if (max == -1) return true; // We don't track winsize for this connection
            return usedWinSize.get(remote) < max;
        }
    }
    public void onWinsizeAckTimeout(String remote) {
        synchronized (winSizeLock) {
            if (!maxWinSize.containsKey(remote)) return;
            usedWinSize.put(remote, Math.max(0, usedWinSize.get(remote) - 1));
            maxWinSize.put(remote, Math.max(MIN_CONNECTION_WINSIZE, maxWinSize.get(remote) / 2));
        }
        final Object remoteLock = trackedLock.get(remote);
        synchronized (remoteLock) {
            remoteLock.notify();
        }
    }
    public void onWinsizeAckReceived(String remote, int rttMs) {
        synchronized (winSizeLock) {
            if (!maxWinSize.containsKey(remote)) return;
            usedWinSize.put(remote, Math.max(0, usedWinSize.get(remote) - 1));
            maxWinSize.put(remote, Math.min(MAX_CONNECTION_WINSIZE, maxWinSize.get(remote) + 1));

            int rto = trackedRto.get(remote);
            //noinspection IntegerDivisionInFloatingPointContext
            trackedRto.put(remote, (int) (rto + Math.tanh((3 * rttMs - rto) / 1000) * 100));
        }
        final Object remoteLock = trackedLock.get(remote);
        synchronized (remoteLock) {
            remoteLock.notify();
        }
    }
    public void sendWinsizeTrackedPacket(String remote) {
        final Object remoteLock = trackedLock.get(remote);
        if (remoteLock == null) return;
        synchronized (remoteLock) {
            while(!isWinSizeAvailable(remote)) {
                try {
                    remoteLock.wait();
                } catch (InterruptedException ignored) {}
            }
        }

        synchronized (winSizeLock) {
            if (usedWinSize.containsKey(remote)) {
                usedWinSize.put(remote, usedWinSize.get(remote) + 1);
            }
        }
    }
    public int getTrackedRto(String remote) {
        synchronized (winSizeLock) {
            return trackedRto.getOrDefault(remote, ConnectionProvider.messageAckTimeoutMS());
        }
    }



    public List<CompletableFuture<SimpleMessagesProtocol.ReceivedMessage>> sendMessageAsync(List<String> destination, ByteString replyTo, Object message) throws NKNClientException.UnknownObjectType {
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

    public List<CompletableFuture<SimpleMessagesProtocol.ReceivedMessage>> sendMessageAsync(List<String> destination, ByteString replyTo, MessagesP.PayloadType type, ByteString message) {
        final ByteString replyToMessageID = replyTo == null ? ByteString.copyFrom(new byte[0]) : replyTo;

        if (ct.forClient.getEncryptionLevel() == NKNClient.EncryptionLevel.CONVERT_MULTICAST_TO_UNICAST_AND_ENCRYPT) {

            final List<CompletableFuture<SimpleMessagesProtocol.ReceivedMessage>> promises = new ArrayList<>();

            for (String d : destination) {
                final ByteString messageID = type == MessagesP.PayloadType.SESSION ? replyTo : ByteString.copyFrom(Crypto.nextRandom4B());

                final MessagesP.Payload payload = MessagesP.Payload.newBuilder()
                        .setType(type)
                        .setPid(messageID)
                        .setReplyToPid(replyToMessageID)
                        .setData(message)
                        .setNoAck(ct.forClient.simpleMessagesProtocol().isNoAutomaticACKs())
                        .build();

                try {
                    final ByteString encryptedPayload = ClientEnc.encryptMessage(Collections.singletonList(d), payload.toByteString(), ct.identity.wallet, NKNClient.EncryptionLevel.ENCRYPT_ONLY_UNICAST);
                    promises.addAll(sendEncryptedMessage(Collections.singletonList(d), messageID, encryptedPayload, type == MessagesP.PayloadType.SESSION));
                } catch (NKNClientException e) {
                    LOG.warn("Failed to send message", e);
                }
            }

            return promises;

        } else {
            final ByteString messageID = type == MessagesP.PayloadType.SESSION ? replyTo : ByteString.copyFrom(Crypto.nextRandom4B());

            final MessagesP.Payload payload = MessagesP.Payload.newBuilder()
                    .setType(type)
                    .setPid(messageID)
                    .setReplyToPid(replyToMessageID)
                    .setData(message)
                    .setNoAck(ct.forClient.simpleMessagesProtocol().isNoAutomaticACKs())
                    .build();


            try {
                final ByteString encryptedPayload = ClientEnc.encryptMessage(destination, payload.toByteString(), ct.identity.wallet, ct.forClient.getEncryptionLevel());

                return sendEncryptedMessage(destination, messageID, encryptedPayload, type == MessagesP.PayloadType.SESSION);
            } catch (NKNClientException e) {
                LOG.warn("Failed to send message", e);

                return Collections.emptyList();
            }
        }
    }

    private List<CompletableFuture<SimpleMessagesProtocol.ReceivedMessage>> sendEncryptedMessage(List<String> destination, ByteString messageID, ByteString payload, boolean noreplyQ) {
        if (destination.size() == 0) throw new IllegalArgumentException("At least one address is required for multicast");

        final ArrayList<CompletableFuture<SimpleMessagesProtocol.ReceivedMessage>> promises = new ArrayList<>();
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
            outboundQ.put(new MessageJob(destination, messageID, msg.toByteString(), promises, ConnectionProvider.messageAckTimeoutMS(), noreplyQ));
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
            final ByteString encryptedPayload = ClientEnc.encryptMessage(Collections.singletonList(destination), payload.toByteString(), ct.identity.wallet, ct.forClient.getEncryptionLevel());

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
        private final List<CompletableFuture<SimpleMessagesProtocol.ReceivedMessage>> promise;
        private final long timeoutIn;
        private long timeoutAt = -1;
        private final boolean noreplyQ;

        MessageJob(List<String> destination, ByteString messageID, ByteString payload, List<CompletableFuture<SimpleMessagesProtocol.ReceivedMessage>> promise, long timeoutIn, boolean noreplyQ) {
            this.destination = destination;
            this.messageID = messageID;
            this.payload = payload;
            this.promise = promise;
            this.timeoutIn = timeoutIn;
            this.noreplyQ = noreplyQ;
        }

    }
}
