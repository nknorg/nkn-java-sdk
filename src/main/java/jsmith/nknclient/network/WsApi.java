package jsmith.nknclient.network;

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
        LOG.debug("WS#{} received message: '{}'", myId,  message);

        if (messageListener != null) {
            final JSONObject messageJson = new JSONObject(message);
            messageListener.accept(messageJson);
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

    private Consumer<JSONObject> messageListener;
    public void setMessageListener(Consumer<JSONObject> listener) {
        this.messageListener = listener;
    }
    private Consumer<ServerHandshake> openListener;
    public void setOpenListener(Consumer<ServerHandshake> listener) {
        this.openListener = listener;
    }

    public void send(JSONObject json) {
        final String str = json.toString();
        LOG.debug("WS#{} sending text: '{}'", myId, str);
        send(str);
    }
}
