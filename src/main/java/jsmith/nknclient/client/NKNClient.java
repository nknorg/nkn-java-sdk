package jsmith.nknclient.client;

import com.google.protobuf.ByteString;
import jsmith.nknclient.Const;
import jsmith.nknclient.network.ClientApi;
import jsmith.nknclient.network.proto.Payloads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
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

    private final Identity identity;
    private final ClientApi clientApi;

    public NKNClient(Identity identity) {
        this(identity, Const.BOOTSTRAP_NODES_RPC);
    }

    public NKNClient(Identity identity, InetSocketAddress[] bootstrapNodesRPC) {
        this.identity = identity;
        this.clientApi = new ClientApi(identity, bootstrapNodesRPC);
    }

    public NKNClient start() {
        clientApi.start();
        return this;
    }

    public NKNClient close() {
        clientApi.close();
        return this;
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

    public CompletableFuture<ReceivedMessage> sendTextMessage(String destinationFullIdentifier, ByteString replyTo, String message) {
        final Payloads.TextData td = Payloads.TextData.newBuilder()
                .setText(message)
                .build();

        return clientApi.sendMessage(Collections.singletonList(destinationFullIdentifier), replyTo, Payloads.PayloadType.TEXT, td.toByteString()).get(0);
    }

    public CompletableFuture<ReceivedMessage> sendBinaryMessage(String destinationFullIdentifier, ByteString replyTo, byte[] message) {
        return sendBinaryMessage(destinationFullIdentifier, replyTo, ByteString.copyFrom(message));
    }

    public CompletableFuture<ReceivedMessage> sendBinaryMessage(String destinationFullIdentifier, ByteString replyTo, ByteString message) {
        return clientApi.sendMessage(Collections.singletonList(destinationFullIdentifier), replyTo, Payloads.PayloadType.BINARY, message).get(0);
    }

    public CompletableFuture<ReceivedMessage> sendMessage(String destinationFullIdentifier, ByteString replyTo, Object message) {
        return clientApi.sendMessage(Collections.singletonList(destinationFullIdentifier), replyTo, message).get(0);
    }

    public List<CompletableFuture<ReceivedMessage>> sendTextMessageMulticast(String[] destinationFullIdentifier, ByteString replyTo, String message) {
        return sendTextMessageMulticast(Arrays.asList(destinationFullIdentifier), replyTo, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMessageMulticast(String[] destinationFullIdentifier, ByteString replyTo, byte[] message) {
        return sendBinaryMessageMulticast(destinationFullIdentifier, replyTo, ByteString.copyFrom(message));
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMessageMulticast(String[] destinationFullIdentifier, ByteString replyTo, ByteString message) {
        return sendBinaryMessageMulticast(Arrays.asList(destinationFullIdentifier), replyTo, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendMessageMulticast(String[] destinationFullIdentifier, ByteString replyTo, Object message) {
        return sendMessageMulticast(Arrays.asList(destinationFullIdentifier), replyTo, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendTextMessageMulticast(List<String> destinationFullIdentifier, ByteString replyTo, String message) {
        final Payloads.TextData td = Payloads.TextData.newBuilder()
                .setText(message)
                .build();

        return clientApi.sendMessage(destinationFullIdentifier, replyTo, Payloads.PayloadType.TEXT, td.toByteString());
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMessageMulticast(List<String> destinationFullIdentifier, ByteString replyTo, byte[] message) {
        return sendBinaryMessageMulticast(destinationFullIdentifier, replyTo, ByteString.copyFrom(message));
    }

    public List<CompletableFuture<ReceivedMessage>> sendBinaryMessageMulticast(List<String> destinationFullIdentifier, ByteString replyTo, ByteString message) {
        return clientApi.sendMessage(destinationFullIdentifier, replyTo, Payloads.PayloadType.BINARY, message);
    }

    public List<CompletableFuture<ReceivedMessage>> sendMessageMulticast(List<String> destinationFullIdentifier, ByteString replyTo, Object message) {
        return clientApi.sendMessage(destinationFullIdentifier, replyTo, message);
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
