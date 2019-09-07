package jsmith.nknsdk.network;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import jsmith.nknsdk.client.NKNClient;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.network.proto.MessagesP;
import jsmith.nknsdk.utils.Crypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 *
 */
public class ClientMessages extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(ClientMessages.class);

    private final ClientTunnel ct;
    private boolean running = false;

    private final int myId;
    public ClientMessages(ClientTunnel ct, int myId) {
        this.ct = ct;
        this.myId = myId;

        setName("ClientMsg-" + myId);
        setDaemon(true);
    }

    private final Object jobLock = new Object();
    private final ArrayList<MessageJob> jobs = new ArrayList<>();
    private final ArrayList<MessageJob> waitingForReply = new ArrayList<>();

    private boolean noAck = false;
    public void setNoAutomaticACKs(boolean noAck) {
        this.noAck = noAck;
    }
    private NKNClient.EncryptionLevel encryptionLevel = NKNClient.EncryptionLevel.CONVERT_MULTICAST_TO_UNICAST_AND_ENCRYPT;
    public void setEncryptionLevel(NKNClient.EncryptionLevel level) {
        this.encryptionLevel = level;
    }

    public void run() {
        running = true;

        while (!scheduledStop.get() || (!jobs.isEmpty() && ct.shouldReconnect())) {
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

                    if (j.timeoutAt != -1) {
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
                    } else {
                        if (nextWake == -1) {
                            nextWake = now + j.timeoutIn;
                        } else {
                            nextWake = Math.min(nextWake, now + j.timeoutIn);
                        }
                    }
                }
            }

            MessageJob j = null;
            try {
                ct.messageHold.await();

                synchronized (jobLock) {
                    if (!jobs.isEmpty()) {
                        j = jobs.remove(0);
                    }
                }
                if (j != null) {
                    j.timeoutAt = System.currentTimeMillis() + j.timeoutIn;
                    waitingForReply.add(j);
                    ct.ws.sendPacket(j.payload);
                    if (nextWake == -1) {
                        nextWake = j.timeoutAt;
                    } else {
                        nextWake = Math.min(nextWake, j.timeoutAt);
                    }
                }
            } catch (InterruptedException ignored) {}

            synchronized (jobLock) {
                if (jobs.isEmpty()) {
                    try {
                        if (!scheduledStop.get()) {
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

    private AtomicBoolean scheduledStop = new AtomicBoolean(false);
    public void close() {
        if (!running) throw new IllegalStateException("Client is not (yet) running, cannot close");

        synchronized (jobLock) {
            scheduledStop.set(true);
            jobLock.notify();
        }
        try {
            join();
        } catch (InterruptedException ignored) {}
        ct.ws.close();
    }
    public boolean isScheduledStop() {
        return scheduledStop.get();
    }

    private Function<NKNClient.ReceivedMessage, Object> onMessageL = null;
    public void onMessage(Function<NKNClient.ReceivedMessage, Object> listener) {
        onMessageL = listener;
    }


    public void onInboundMessage(String from, MessagesP.Payload message) {
        final MessagesP.PayloadType type = message.getType();
        final ByteString replyTo = message.getReplyToPid();
        final ByteString messageID = message.getPid();

        MessagesP.EncryptedMessage encryptedMessage;
        try {
            encryptedMessage = MessagesP.EncryptedMessage.parseFrom(message.getData());
        } catch (InvalidProtocolBufferException e) {
            LOG.warn("Received invalid message, ignoring");
            return;
        }
        final boolean encrypted = encryptedMessage.getEncrypted();

        ByteString data;
        try {
            data = ClientEnc.decryptMessage(from, encryptedMessage, ct.identity.wallet);
        } catch (NKNClientException e) {
            LOG.warn("Failed to decrypt message, dropping");
            return;
        }


        synchronized (jobLock) {
            for (MessageJob j : waitingForReply) {
                if (j.messageID.equals(replyTo)) {
                    final int indexOf = j.destination.indexOf(from);
                    if (type == MessagesP.PayloadType.TEXT) {
                        try {
                            j.ack.set(indexOf,
                                    new NKNClient.ReceivedMessage(
                                        from,
                                        messageID,
                                        encrypted,
                                        MessagesP.PayloadType.TEXT,
                                        MessagesP.TextData.parseFrom(data).getText()
                                ));
                        } catch (InvalidProtocolBufferException e) {
                            LOG.warn("Received packet is of type TEXT but does not contain valid text data");
                        }
                    } else if (type == MessagesP.PayloadType.BINARY) {
                        j.ack.set(indexOf,
                                new NKNClient.ReceivedMessage(
                                    from,
                                    messageID,
                                    encrypted,
                                    MessagesP.PayloadType.BINARY,
                                    data
                            ));
                    } else if (type == MessagesP.PayloadType.ACK) {
                        j.ack.set(indexOf,
                                new NKNClient.ReceivedMessage(
                                        from,
                                        messageID,
                                        false,
                                        MessagesP.PayloadType.ACK,
                                        null
                                ));
                    }
                    j.receivedAck.set(indexOf, true);
                }
            }

            jobLock.notify();
        }

        Object ackMessage = null;

        if (type == MessagesP.PayloadType.TEXT) {
            try {
                if (onMessageL != null) {
                    ackMessage = onMessageL.apply(new NKNClient.ReceivedMessage(from, messageID, encrypted, type, MessagesP.TextData.parseFrom(data).getText()));
                }
            } catch (InvalidProtocolBufferException e) {
                LOG.warn("Received packet is of type TEXT but does not contain valid text data");
            }
        } else if (type == MessagesP.PayloadType.BINARY) {
            if (onMessageL != null) {
                ackMessage = onMessageL.apply(new NKNClient.ReceivedMessage(from, messageID, encrypted, type, data));
            }
        }

        if (type != MessagesP.PayloadType.ACK) {
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
                        .setData(ClientEnc.encryptMessage(Collections.singletonList(d), message, ct.identity.wallet, NKNClient.EncryptionLevel.ENCRYPT_ONLY_UNICAST))
                        .setNoAck(noAck)
                        .build();

                promises.addAll(sendOutboundMessage(Collections.singletonList(d), messageID, payload.toByteString()));
            }

            return promises;

        } else {
            final ByteString messageID = ByteString.copyFrom(Crypto.nextRandom4B());

            final MessagesP.Payload payload = MessagesP.Payload.newBuilder()
                    .setType(type)
                    .setPid(messageID)
                    .setReplyToPid(replyToMessageID)
                    .setData(ClientEnc.encryptMessage(destination, message, ct.identity.wallet, encryptionLevel))
                    .setNoAck(noAck)
                    .build();


            return sendOutboundMessage(destination, messageID, payload.toByteString());
        }
    }

    private List<CompletableFuture<NKNClient.ReceivedMessage>> sendOutboundMessage(List<String> destination, ByteString messageID, ByteString payload) {
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

        final MessageJob j = new MessageJob(new ArrayList<>(destination), messageID, msg.toByteString(), new ArrayList<>(promises), ConnectionProvider.messageAckTimeoutMS());

        LOG.debug("Queueing new MessageJob");
        synchronized (jobLock) {
            jobs.add(j);
            jobLock.notify();
        }

        return promises;
    }

    public void sendAckMessage(String destination, ByteString replyTo) {
        final MessagesP.Payload payload = MessagesP.Payload.newBuilder()
                .setType(MessagesP.PayloadType.ACK)
                .setPid(ByteString.copyFrom(Crypto.nextRandom4B()))
                .setReplyToPid(replyTo)
                .setNoAck(true)
                .build();

        final MessagesP.ClientMsg.Builder clientToNodeMsg = MessagesP.ClientMsg.newBuilder()
                .setPayload(payload.toByteString())
                .addDests(destination)
                .setMaxHoldingSeconds(0);

        ClientEnc.signOutboundMessage(clientToNodeMsg, ct);

        final MessagesP.Message msg = MessagesP.Message.newBuilder()
                .setMessage(clientToNodeMsg.build().toByteString())
                .setMessageType(MessagesP.MessageType.CLIENT_MSG)
                .build();

        ct.ws.sendPacket(msg.toByteString());
    }


    private static class MessageJob {

        private final List<String> destination;
        private final ByteString messageID, payload;
        private final List<CompletableFuture<NKNClient.ReceivedMessage>> promise;
        private final long timeoutIn;
        private long timeoutAt = -1;

        private final List<Boolean> receivedAck = new ArrayList<>();
        private final List<NKNClient.ReceivedMessage> ack = new ArrayList<>();

        MessageJob(List<String> destination, ByteString messageID, ByteString payload, List<CompletableFuture<NKNClient.ReceivedMessage>> promise, long timeoutIn) {
            this.destination = destination;
            this.messageID = messageID;
            this.payload = payload;
            this.promise = promise;
            this.timeoutIn = timeoutIn;

            for (String ignored : destination) {
                receivedAck.add(false);
                ack.add(null);
            }
        }

    }
}
