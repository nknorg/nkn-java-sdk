package jsmith.nknclient.client;

import com.google.protobuf.ByteString;
import jsmith.nknclient.network.ClientApi;
import jsmith.nknclient.network.proto.Payloads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final Identity identity;
    private final ClientApi clientApi;

    public NKNClient(Identity identity) {
        this.identity = identity;
        this.clientApi = new ClientApi(identity);
    }

    public NKNClient start() throws NKNClientException {
        clientApi.startClient();
        return this;
    }

    public void close() {
        clientApi.close();
    }

    public NKNClient onNewMessage(Consumer<ReceivedMessage> listener) {
        clientApi.onMessage((msg) -> {
            listener.accept(msg);
            return null;
        });
        return this;
    }

    public NKNClient onNewMessageWithReply(Function<ReceivedMessage, Object> listener) {
        clientApi.onMessage(listener);
        return this;
    }

    private boolean noAutomaticACKs = false;
    public void setNoAutomaticACKs(boolean noAutomaticACKs) {
        clientApi.setNoAutomaticACKs(noAutomaticACKs);
        this.noAutomaticACKs  = noAutomaticACKs;
    }
    public boolean isNoAutomaticACKs() {
        return noAutomaticACKs;
    }

    public CompletableFuture<ReceivedMessage> sendTextMessageAsync(String destinationFullIdentifier, ByteString replyTo, String message) {
        final Payloads.TextData td = Payloads.TextData.newBuilder()
                .setText(message)
                .build();

        LOG.debug("Sending text message: {}", message);
        return clientApi.sendMessageAsync(Collections.singletonList(destinationFullIdentifier), replyTo, Payloads.PayloadType.TEXT, td.toByteString()).get(0);
    }

    public CompletableFuture<ReceivedMessage> sendBinaryMessageAsync(String destinationFullIdentifier, ByteString replyTo, byte[] message) {
        return sendBinaryMessageAsync(destinationFullIdentifier, replyTo, ByteString.copyFrom(message));
    }

    public CompletableFuture<ReceivedMessage> sendBinaryMessageAsync(String destinationFullIdentifier, ByteString replyTo, ByteString message) {
        LOG.debug("Sending binary message");
        return clientApi.sendMessageAsync(Collections.singletonList(destinationFullIdentifier), replyTo, Payloads.PayloadType.BINARY, message).get(0);
    }

    public CompletableFuture<ReceivedMessage> sendMessageAsync(String destinationFullIdentifier, ByteString replyTo, Object message) {
        LOG.debug("Sending multicast message");
        return clientApi.sendMessageAsync(Collections.singletonList(destinationFullIdentifier), replyTo, message).get(0);
    }

    public List<CompletableFuture<ReceivedMessage>> sendTextMessageMulticastAsync(String[] destinationFullIdentifier, ByteString replyTo, String message) {
        return sendTextMessageMulticastAsync(Arrays.asList(destinationFullIdentifier), replyTo, message);
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

    public List<CompletableFuture<ReceivedMessage>> sendTextMessageMulticastAsync(List<String> destinationFullIdentifier, ByteString replyTo, String message) {
        final Payloads.TextData td = Payloads.TextData.newBuilder()
                .setText(message)
                .build();

        LOG.debug("Sending multicast text message: {}", message);
        return clientApi.sendMessageAsync(destinationFullIdentifier, replyTo, Payloads.PayloadType.TEXT, td.toByteString());
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMessageMulticastAsync(List<String> destinationFullIdentifier, ByteString replyTo, byte[] message) {
        return sendBinaryMessageMulticastAsync(destinationFullIdentifier, replyTo, ByteString.copyFrom(message));
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMessageMulticastAsync(List<String> destinationFullIdentifier, ByteString replyTo, ByteString message) {
        LOG.debug("Sending multicast binary message");
        return clientApi.sendMessageAsync(destinationFullIdentifier, replyTo, Payloads.PayloadType.BINARY, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendMessageMulticastAsync(List<String> destinationFullIdentifier, ByteString replyTo, Object message) {
        LOG.debug("Sending multicast message");
        return clientApi.sendMessageAsync(destinationFullIdentifier, replyTo, message);
    }

    public String getCurrentSigChainBlockHash() {
        return clientApi.currentSigChainBlockHash();
    }

    public static class ReceivedMessage {

        public final ByteString msgId;
        public final String from;

        public final ByteString binaryData;
        public final String textData;
        public final boolean isBinary;
        public final boolean isText;
        public final boolean isAck;

        public ReceivedMessage(String from, ByteString msgId, Payloads.PayloadType type, Object data) {
            this.from = from;
            this.msgId = msgId;
            if (type == Payloads.PayloadType.TEXT) {
                isText = true;
                textData = (String) data;

                isAck = false;
                isBinary = false;
                binaryData = null;
            } else if (type == Payloads.PayloadType.BINARY) {
                isBinary = true;
                binaryData = (ByteString) data;

                isAck = false;
                isText = false;
                textData = null;
            } else if (type == Payloads.PayloadType.ACK) {
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
