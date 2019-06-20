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
    public void setNoAutomaticACKs(boolean noAutomaticACKs) {
        clientMessages.setNoAutomaticACKs(noAutomaticACKs);
        this.noAutomaticACKs  = noAutomaticACKs;
    }
    public boolean isNoAutomaticACKs() {
        return noAutomaticACKs;
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

    public List<CompletableFuture<ReceivedMessage>> publishTextMessageAsync(String topic, int bucket, String message) throws WalletException {
        final MessagesP.TextData td = MessagesP.TextData.newBuilder()
                .setText(message)
                .build();

        return publishMessageAsync(topic, bucket, td.toByteString(), MessagesP.PayloadType.TEXT);
    }

    public List<CompletableFuture<ReceivedMessage>> publishBinaryMessageAsync(String topic, int bucket, byte[] message) throws WalletException {
        return publishBinaryMessageAsync(topic, bucket, ByteString.copyFrom(message));
    }

    public List<CompletableFuture<ReceivedMessage>> publishBinaryMessageAsync(String topic, int bucket, ByteString message) throws WalletException {
        return publishMessageAsync(topic, bucket, message, MessagesP.PayloadType.BINARY);
    }

    private List<CompletableFuture<ReceivedMessage>> publishMessageAsync(String topic, int bucket, ByteString data, MessagesP.PayloadType type) throws WalletException {
        final NKNExplorer.Subscriber[] subscribers = NKNExplorer.getSubscribers(topic, bucket);
        if (subscribers.length == 0) return new ArrayList<>();

        final ArrayList<String> dest = new ArrayList<>(subscribers.length);
        for (NKNExplorer.Subscriber sub : subscribers) dest.add(sub.fullClientIdentifier);

        LOG.debug("Publishing binary message");
        return clientMessages.sendMessageAsync(dest, null, type, data);
    }

    public String getCurrentSigChainBlockHash() {
        return clientTunnel.currentSigChainBlockHash();
    }

    public static class ReceivedMessage {

        public final ByteString msgId;
        public final String from;

        public final ByteString binaryData;
        public final String textData;
        public final boolean isBinary;
        public final boolean isText;
        public final boolean isAck;

        public ReceivedMessage(String from, ByteString msgId, MessagesP.PayloadType type, Object data) {
            this.from = from;
            this.msgId = msgId;
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

}
