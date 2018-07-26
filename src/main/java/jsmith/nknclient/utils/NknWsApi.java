package jsmith.nknclient.utils;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.function.Consumer;

/**
 *
 */
public class NknWsApi extends WebSocketClient {

    private static final Logger LOG = LoggerFactory.getLogger(NknWsApi.class);
    private static final boolean VERBOSE_LOGGING = true;


    private static int id = 0;
    private final int myId;


    public NknWsApi(InetSocketAddress address) {
        super(URI.create("ws://" + address.getHostString() + ":" + address.getPort()));
        myId = ++id;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        if (VERBOSE_LOGGING) LOG.debug("WS#{} open, addr: '{}'", myId, getRemoteSocketAddress());

        if (openListener != null) {
            openListener.accept(handshakedata);
        }
    }

    @Override
    public void onMessage(String message) {
        if (VERBOSE_LOGGING) LOG.debug("WS#{} received message: '{}'", myId,  message);

        if (messageListener != null) {
            final JSONObject messageJson = new JSONObject(message);
            messageListener.accept(messageJson);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (VERBOSE_LOGGING) LOG.debug("WS#{} closed", myId);
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
        if (VERBOSE_LOGGING) LOG.debug("WS#{} sending: '{}'", myId, str);
        send(str);
    }
}
