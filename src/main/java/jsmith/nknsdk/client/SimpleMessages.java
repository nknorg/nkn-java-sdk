package jsmith.nknsdk.client;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.network.ClientMessageWorkers;
import jsmith.nknsdk.network.proto.MessagesP;
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
public class SimpleMessages {

    // TODO allocate message to multiclients

    private static final Logger LOG = LoggerFactory.getLogger(SimpleMessages.class);

    private final ClientMessageWorkers cmWorkers;
    SimpleMessages(ClientMessageWorkers cmWorkers) {
        this.cmWorkers = cmWorkers;
    }

    private Function<SimpleMessages.ReceivedMessage, Object> onMessageL = null;
    public SimpleMessages onNewMessage(Consumer<ReceivedMessage> listener) {
        onMessageL = (msg) -> {
            listener.accept(msg);
            return null;
        };
        return this;
    }

    public SimpleMessages onNewMessageWithReply(Function<ReceivedMessage, Object> listener) {
        onMessageL = listener;
        return this;
    }
    public Function<SimpleMessages.ReceivedMessage, Object> getOnMessageListener() {
        return onMessageL;
    }

    private boolean noAutomaticACKs = false;
    public SimpleMessages setNoAutomaticACKs(boolean noAutomaticACKs) {
        this.noAutomaticACKs  = noAutomaticACKs;
        return this;
    }
    public boolean isNoAutomaticACKs() {
        return noAutomaticACKs;
    }


    public CompletableFuture<ReceivedMessage> sendTextAsync(String destinationFullIdentifier, String message) {
        return sendTextAsync(destinationFullIdentifier, null, message);
    }

    public CompletableFuture<ReceivedMessage> sendTextAsync(String destinationFullIdentifier, ByteString replyTo, String message) {
        final MessagesP.TextData td = MessagesP.TextData.newBuilder()
                .setText(message)
                .build();

        LOG.debug("Sending text message: {}", message);
        return cmWorkers.sendMessageAsync(Collections.singletonList(destinationFullIdentifier), replyTo, MessagesP.PayloadType.TEXT, td.toByteString()).get(0);
    }

    public CompletableFuture<ReceivedMessage> sendBinaryAsync(String destinationFullIdentifier, byte[] message) {
        return sendBinaryAsync(destinationFullIdentifier, null, message);
    }

    public CompletableFuture<ReceivedMessage> sendBinaryAsync(String destinationFullIdentifier, ByteString message) {
        return sendBinaryAsync(destinationFullIdentifier, null, message);
    }

    public CompletableFuture<ReceivedMessage> sendBinaryAsync(String destinationFullIdentifier, ByteString replyTo, byte[] message) {
        return sendBinaryAsync(destinationFullIdentifier, replyTo, ByteString.copyFrom(message));
    }

    public CompletableFuture<ReceivedMessage> sendBinaryAsync(String destinationFullIdentifier, ByteString replyTo, ByteString message) {
        LOG.debug("Sending binary message");
        return cmWorkers.sendMessageAsync(Collections.singletonList(destinationFullIdentifier), replyTo, MessagesP.PayloadType.BINARY, message).get(0);
    }

    public CompletableFuture<ReceivedMessage> sendAsync(String destinationFullIdentifier, ByteString replyTo, Object message) {
        LOG.debug("Sending multicast message");
        return cmWorkers.sendMessageAsync(Collections.singletonList(destinationFullIdentifier), replyTo, message).get(0);
    }

    public List<CompletableFuture<ReceivedMessage>> sendTextMulticastAsync(String[] destinationFullIdentifier, String message) {
        return sendTextMulticastAsync(destinationFullIdentifier, null, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendTextMulticastAsync(String[] destinationFullIdentifier, ByteString replyTo, String message) {
        return sendTextMulticastAsync(Arrays.asList(destinationFullIdentifier), replyTo, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMulticastAsync(String[] destinationFullIdentifier, byte[] message) {
        return sendBinaryMulticastAsync(destinationFullIdentifier, null, ByteString.copyFrom(message));
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMulticastAsync(String[] destinationFullIdentifier, ByteString message) {
        return sendBinaryMulticastAsync(destinationFullIdentifier, null, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMulticastAsync(String[] destinationFullIdentifier, ByteString replyTo, byte[] message) {
        return sendBinaryMulticastAsync(destinationFullIdentifier, replyTo, ByteString.copyFrom(message));
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMulticastAsync(String[] destinationFullIdentifier, ByteString replyTo, ByteString message) {
        return sendBinaryMulticastAsync(Arrays.asList(destinationFullIdentifier), replyTo, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendMulticastAsync(String[] destinationFullIdentifier, ByteString replyTo, Object message) {
        return sendMulticastAsync(Arrays.asList(destinationFullIdentifier), replyTo, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendTextMulticastAsync(List<String> destinationFullIdentifier, String message) {
        return sendTextMulticastAsync(destinationFullIdentifier, null, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendTextMulticastAsync(List<String> destinationFullIdentifier, ByteString replyTo, String message) {
        final MessagesP.TextData td = MessagesP.TextData.newBuilder()
                .setText(message)
                .build();

        LOG.debug("Sending multicast text message: {}", message);
        return cmWorkers.sendMessageAsync(destinationFullIdentifier, replyTo, MessagesP.PayloadType.TEXT, td.toByteString());
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMulticastAsync(List<String> destinationFullIdentifier, ByteString replyTo, byte[] message) {
        return sendBinaryMulticastAsync(destinationFullIdentifier, replyTo, ByteString.copyFrom(message));
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMulticastAsync(List<String> destinationFullIdentifier, ByteString replyTo, ByteString message) {
        LOG.debug("Sending multicast binary message");
        return cmWorkers.sendMessageAsync(destinationFullIdentifier, replyTo, MessagesP.PayloadType.BINARY, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendMulticastAsync(List<String> destinationFullIdentifier, ByteString replyTo, Object message) {
        LOG.debug("Sending multicast message");
        return cmWorkers.sendMessageAsync(destinationFullIdentifier, replyTo, message);
    }







    public List<CompletableFuture<ReceivedMessage>> publishTextAsync(String topic, boolean includeTxPool, String message) throws NKNExplorerException {
        final MessagesP.TextData td = MessagesP.TextData.newBuilder()
                .setText(message)
                .build();

        return publishAsync(topic, includeTxPool, td.toByteString(), MessagesP.PayloadType.TEXT);
    }

    public List<CompletableFuture<ReceivedMessage>> publishBinaryAsync(String topic, boolean includeTxPool, byte[] message) throws NKNExplorerException {
        return publishBinaryAsync(topic, includeTxPool, ByteString.copyFrom(message));
    }

    public List<CompletableFuture<ReceivedMessage>> publishBinaryAsync(String topic, boolean includeTxPool, ByteString message) throws NKNExplorerException {
        return publishAsync(topic, includeTxPool, message, MessagesP.PayloadType.BINARY);
    }

    private List<CompletableFuture<ReceivedMessage>> publishAsync(String topic, boolean includeTxPool, ByteString data, MessagesP.PayloadType type) throws NKNExplorerException {
        final NKNExplorer.Subscription.Subscriber[] subscribers = NKNExplorer.Subscription.getSubscribers(topic, 0, NKNExplorer.Subscription.MAX_LIMIT, false, includeTxPool);
        if (subscribers.length == 0) return new ArrayList<>();

        final ArrayList<String> dest = new ArrayList<>(subscribers.length);
        for (NKNExplorer.Subscription.Subscriber sub : subscribers) dest.add(sub.fullClientIdentifier);

        LOG.debug("Publishing message");
        return cmWorkers.sendMessageAsync(dest, null, type, data);
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

}
