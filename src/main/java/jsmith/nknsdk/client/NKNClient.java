package jsmith.nknsdk.client;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.network.ClientMessages;
import jsmith.nknsdk.network.ClientTunnel;
import jsmith.nknsdk.network.proto.MessagesP;
import jsmith.nknsdk.wallet.WalletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;


/**
 *
 */
public class NKNClient {

    private static final Logger LOG = LoggerFactory.getLogger(NKNClient.class);

    private final ClientTunnel clientTunnel;
    private ClientMessages clientMessages;

    public NKNClient(Identity identity) {
        this.clientTunnel = new ClientTunnel(identity);
        this.clientMessages = clientTunnel.getAssociatedCM();
    }

    public NKNClient start() throws NKNClientException {
        clientTunnel.startClient();
        return this;
    }

    public void close() {
        clientMessages.close();
    }

    public NKNClient onNewMessage(Consumer<ReceivedMessage> listener) {
        clientMessages.onMessage((msg) -> {
            listener.accept(msg);
            return null;
        });
        return this;
    }

    public NKNClient onNewMessageWithReply(Function<ReceivedMessage, Object> listener) {
        clientMessages.onMessage(listener);
        return this;
    }

    private boolean noAutomaticACKs = false;
    public NKNClient setNoAutomaticACKs(boolean noAutomaticACKs) {
        clientMessages.setNoAutomaticACKs(noAutomaticACKs);
        this.noAutomaticACKs  = noAutomaticACKs;
        return this;
    }
    public boolean isNoAutomaticACKs() {
        return noAutomaticACKs;
    }

    private EncryptionLevel encryptionLevel = EncryptionLevel.ENCRYPT_ONLY_UNICAST;
    public NKNClient setEncryptionLevel(EncryptionLevel level) {
        clientMessages.setEncryptionLevel(level);
        this.encryptionLevel = level;
        return this;
    }
    public EncryptionLevel getEncryptionLevel() {
        return encryptionLevel;
    }

    public CompletableFuture<ReceivedMessage> sendTextMessageAsync(String destinationFullIdentifier, String message) {
        return sendTextMessageAsync(destinationFullIdentifier, null, message);
    }

    public CompletableFuture<ReceivedMessage> sendTextMessageAsync(String destinationFullIdentifier, ByteString replyTo, String message) {
        final MessagesP.TextData td = MessagesP.TextData.newBuilder()
                .setText(message)
                .build();

        LOG.debug("Sending text message: {}", message);
        return clientMessages.sendMessageAsync(Collections.singletonList(destinationFullIdentifier), replyTo, MessagesP.PayloadType.TEXT, td.toByteString()).get(0);
    }

    public CompletableFuture<ReceivedMessage> sendBinaryMessageAsync(String destinationFullIdentifier, byte[] message) {
        return sendBinaryMessageAsync(destinationFullIdentifier, null, message);
    }

    public CompletableFuture<ReceivedMessage> sendBinaryMessageAsync(String destinationFullIdentifier, ByteString message) {
        return sendBinaryMessageAsync(destinationFullIdentifier, null, message);
    }

    public CompletableFuture<ReceivedMessage> sendBinaryMessageAsync(String destinationFullIdentifier, ByteString replyTo, byte[] message) {
        return sendBinaryMessageAsync(destinationFullIdentifier, replyTo, ByteString.copyFrom(message));
    }

    public CompletableFuture<ReceivedMessage> sendBinaryMessageAsync(String destinationFullIdentifier, ByteString replyTo, ByteString message) {
        LOG.debug("Sending binary message");
        return clientMessages.sendMessageAsync(Collections.singletonList(destinationFullIdentifier), replyTo, MessagesP.PayloadType.BINARY, message).get(0);
    }

    public CompletableFuture<ReceivedMessage> sendMessageAsync(String destinationFullIdentifier, ByteString replyTo, Object message) {
        LOG.debug("Sending multicast message");
        return clientMessages.sendMessageAsync(Collections.singletonList(destinationFullIdentifier), replyTo, message).get(0);
    }

    public List<CompletableFuture<ReceivedMessage>> sendTextMessageMulticastAsync(String[] destinationFullIdentifier, String message) {
        return sendTextMessageMulticastAsync(destinationFullIdentifier, null, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendTextMessageMulticastAsync(String[] destinationFullIdentifier, ByteString replyTo, String message) {
        return sendTextMessageMulticastAsync(Arrays.asList(destinationFullIdentifier), replyTo, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMessageMulticastAsync(String[] destinationFullIdentifier, byte[] message) {
        return sendBinaryMessageMulticastAsync(destinationFullIdentifier, null, ByteString.copyFrom(message));
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMessageMulticastAsync(String[] destinationFullIdentifier, ByteString message) {
        return sendBinaryMessageMulticastAsync(destinationFullIdentifier, null, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMessageMulticastAsync(String[] destinationFullIdentifier, ByteString replyTo, byte[] message) {
        return sendBinaryMessageMulticastAsync(destinationFullIdentifier, replyTo, ByteString.copyFrom(message));
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMessageMulticastAsync(String[] destinationFullIdentifier, ByteString replyTo, ByteString message) {
        return sendBinaryMessageMulticastAsync(Arrays.asList(destinationFullIdentifier), replyTo, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendMessageMulticastAsync(String[] destinationFullIdentifier, ByteString replyTo, Object message) {
        return sendMessageMulticastAsync(Arrays.asList(destinationFullIdentifier), replyTo, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendTextMessageMulticastAsync(List<String> destinationFullIdentifier, String message) {
        return sendTextMessageMulticastAsync(destinationFullIdentifier, null, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendTextMessageMulticastAsync(List<String> destinationFullIdentifier, ByteString replyTo, String message) {
        final MessagesP.TextData td = MessagesP.TextData.newBuilder()
                .setText(message)
                .build();

        LOG.debug("Sending multicast text message: {}", message);
        return clientMessages.sendMessageAsync(destinationFullIdentifier, replyTo, MessagesP.PayloadType.TEXT, td.toByteString());
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMessageMulticastAsync(List<String> destinationFullIdentifier, ByteString replyTo, byte[] message) {
        return sendBinaryMessageMulticastAsync(destinationFullIdentifier, replyTo, ByteString.copyFrom(message));
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMessageMulticastAsync(List<String> destinationFullIdentifier, ByteString replyTo, ByteString message) {
        LOG.debug("Sending multicast binary message");
        return clientMessages.sendMessageAsync(destinationFullIdentifier, replyTo, MessagesP.PayloadType.BINARY, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendMessageMulticastAsync(List<String> destinationFullIdentifier, ByteString replyTo, Object message) {
        LOG.debug("Sending multicast message");
        return clientMessages.sendMessageAsync(destinationFullIdentifier, replyTo, message);
    }

    public List<CompletableFuture<ReceivedMessage>> publishTextMessageAsync(String topic, boolean includeTxPool, String message) throws NKNExplorerException {
        final MessagesP.TextData td = MessagesP.TextData.newBuilder()
                .setText(message)
                .build();

        return publishMessageAsync(topic, includeTxPool, td.toByteString(), MessagesP.PayloadType.TEXT);
    }

    public List<CompletableFuture<ReceivedMessage>> publishBinaryMessageAsync(String topic, boolean includeTxPool, byte[] message) throws NKNExplorerException {
        return publishBinaryMessageAsync(topic, includeTxPool, ByteString.copyFrom(message));
    }

    public List<CompletableFuture<ReceivedMessage>> publishBinaryMessageAsync(String topic, boolean includeTxPool, ByteString message) throws NKNExplorerException {
        return publishMessageAsync(topic, includeTxPool, message, MessagesP.PayloadType.BINARY);
    }

    private List<CompletableFuture<ReceivedMessage>> publishMessageAsync(String topic, boolean includeTxPool, ByteString data, MessagesP.PayloadType type) throws NKNExplorerException {
        final NKNExplorer.Subscription.Subscriber[] subscribers = NKNExplorer.Subscription.getSubscribers(topic, 0, NKNExplorer.Subscription.MAX_LIMIT, false, includeTxPool);
        if (subscribers.length == 0) return new ArrayList<>();

        final ArrayList<String> dest = new ArrayList<>(subscribers.length);
        for (NKNExplorer.Subscription.Subscriber sub : subscribers) dest.add(sub.fullClientIdentifier);

        LOG.debug("Publishing message");
        return clientMessages.sendMessageAsync(dest, null, type, data);
    }

    public ByteString getCurrentSigChainBlockHash() {
        return clientTunnel.currentSigChainBlockHash();
    }

    public static class ReceivedMessage {

        public final ByteString msgId;
        public final String from;
        public final boolean wasEncrypted;

        public final ByteString binaryData;
        public final String textData;
        public final boolean isBinary;
        public final boolean isText;
        public final boolean isAck;

        public ReceivedMessage(String from, ByteString msgId, boolean wasEncrypted, MessagesP.PayloadType type, Object data) {
            this.from = from.lastIndexOf('.') == 0 ? from.substring(1) : from;
            this.msgId = msgId;
            this.wasEncrypted = wasEncrypted;

            if (type == MessagesP.PayloadType.TEXT) {
                isText = true;
                textData = (String) data;

                isAck = false;
                isBinary = false;
                binaryData = null;
            } else if (type == MessagesP.PayloadType.BINARY) {
                isBinary = true;
                binaryData = (ByteString) data;

                isAck = false;
                isText = false;
                textData = null;
            } else if (type == MessagesP.PayloadType.ACK) {
                isAck = true;

                isText = false;
                textData = null;
                isBinary = false;
                binaryData = null;
            } else {
                isAck = false;
                isText = false;
                textData = null;
                isBinary = false;
                binaryData = null;
            }

        }

    }

    public enum EncryptionLevel {

        DO_NOT_ENCRYPT,
        ENCRYPT_ONLY_UNICAST,
        ENCRYPT_UNICAST_AND_MULTICAST

    }

}
