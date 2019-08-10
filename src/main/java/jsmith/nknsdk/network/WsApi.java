package jsmith.nknsdk.network;

import com.google.protobuf.ByteString;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 *
 */
public class WsApi extends WebSocketClient {

    private static final Logger LOG = LoggerFactory.getLogger(WsApi.class);

    private static int id = 0;
    private final int myId;


    public WsApi(InetSocketAddress address) {
        super(URI.create("ws://" + address.getHostString() + ":" + address.getPort()));
        myId = ++id;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        LOG.debug("WS#{} open, addr: '{}'", myId, super.uri);
        if (openListener != null) {
            openListener.run();
        }
    }

    public void connect() {
        super.connect();
    }



    public void onMessage(String message) {
        LOG.debug("WS#{} received text message: '{}'", myId,  message);

        if (jsonMessageListener != null) {
            final JSONObject messageJson = new JSONObject(message);
            jsonMessageListener.accept(messageJson);
        }
    }

    public void onMessage(ByteBuffer bytes) {
        LOG.debug("WS#{} received bin message, {} bytes", myId, bytes.limit());

        if (protobufMessageListener != null) {
            protobufMessageListener.accept(ByteString.copyFrom(bytes));
        }
    }



    @Override
    public void onClose(int code, String reason, boolean remote) {
        LOG.debug("WS#{} closed, {}", myId, reason);

        if (closeListener != null) {
            closeListener.accept(reason);
        }
    }

    @Override
    public void onError(Exception ex) {
        LOG.warn("WS#{} On error: {}", myId, ex);
        // TODO handle error
    }

    public void close() {
        LOG.warn("WS#{} Closing", myId);
        super.close();
    }

    private Consumer<JSONObject> jsonMessageListener;
    public void setJsonMessageListener(Consumer<JSONObject> listener) {
        this.jsonMessageListener = listener;
    }
    private Consumer<ByteString> protobufMessageListener;
    public void setProtobufMessageListener(Consumer<ByteString> listener) {
        this.protobufMessageListener = listener;
    }
    private Runnable openListener;
    private Consumer<String> closeListener;
    public void setCLoseListener(Consumer<String> listener) {
        this.closeListener = listener;
    }
    public void setOpenListener(Runnable listener) {
        this.openListener = listener;
    }

    public void sendPacket(ByteString bin) {
        LOG.debug("WS#{} sending bin, {} bytes", myId, bin.size());
        send(ByteBuffer.wrap(bin.toByteArray()));
    }

    public void sendPacket(JSONObject json) {
        final String str = json.toString();
        send(str);
    }
}
