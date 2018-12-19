package jsmith.nknclient.network;

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
        LOG.debug("WS#{} open, addr: '{}'", myId, getRemoteSocketAddress());

        if (openListener != null) {
            openListener.accept(handshakedata);
        }
    }

    @Override
    public void onMessage(String message) {
        LOG.debug("WS#{} received text message: '{}'", myId,  message);

        if (jsonMessageListener != null) {
            final JSONObject messageJson = new JSONObject(message);
            jsonMessageListener.accept(messageJson);
        }
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        LOG.debug("WS#{} received bin message, {} bytes", myId, bytes.limit());

        if (protobufMessageListener != null) {
            protobufMessageListener.accept(ByteString.copyFrom(bytes));
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        LOG.debug("WS#{} closed", myId);
    }

    @Override
    public void onError(Exception ex) {
        LOG.error("WS#{} error", myId, ex);
    }

    private Consumer<JSONObject> jsonMessageListener;
    public void setJsonMessageListener(Consumer<JSONObject> listener) {
        this.jsonMessageListener = listener;
    }
    private Consumer<ByteString> protobufMessageListener;
    public void setProtobufMessageListener(Consumer<ByteString> listener) {
        this.protobufMessageListener = listener;
    }
    private Consumer<ServerHandshake> openListener;
    public void setOpenListener(Consumer<ServerHandshake> listener) {
        this.openListener = listener;
    }

    public void sendPacket(ByteString bin) {
        LOG.debug("WS#{} sending bin, {} bytes", myId, bin.size());
        send(bin.toByteArray());
    }

    public void sendPacket(JSONObject json) {
        final String str = json.toString();
        LOG.debug("WS#{} sending text: '{}'", myId, str);
        send(str);
    }
}
