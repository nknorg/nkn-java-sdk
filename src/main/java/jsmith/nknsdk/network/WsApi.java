package jsmith.nknsdk.network;

import com.google.protobuf.ByteString;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 *
 */
@ClientEndpoint
public class WsApi {

    private static final Logger LOG = LoggerFactory.getLogger(WsApi.class);

    private static int id = 0;
    private final int myId;

    private Session session = null;
    private final WebSocketContainer container;
    private final URI serverURI;


    public WsApi(InetSocketAddress address) {
        myId = ++id;
        serverURI = URI.create("ws://" + address.getHostString() + ":" + address.getPort());
        container = ContainerProvider.getWebSocketContainer();
    }

    public void connect() {
        try {
            this.session = container.connectToServer(this, serverURI);

            LOG.debug("WS#{} open, addr: '{}'", myId, serverURI);
            if (openListener != null) {
                openListener.run();
            }
        } catch (DeploymentException de) {
            LOG.warn("WS#{} failed to connect: {}", myId, de);
            onClose(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "Crashed"));
        } catch (IOException ioe) {
            LOG.warn("WS#{}, IOE when connecting", myId, ioe);
            onClose(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "Crashed"));
        }
    }

    @OnMessage
    public void onMessage(String message) {
        LOG.debug("WS#{} received text message: '{}'", myId,  message);

        if (jsonMessageListener != null) {
            final JSONObject messageJson = new JSONObject(message);
            jsonMessageListener.accept(messageJson);
        }
    }

    @OnMessage
    public void onMessage(ByteBuffer bytes) {
        LOG.debug("WS#{} received bin message, {} bytes", myId, bytes.limit());

        if (protobufMessageListener != null) {
            protobufMessageListener.accept(ByteString.copyFrom(bytes));
        }
    }

    @OnClose
    public void onClose(CloseReason reason) {
        LOG.debug("WS#{} closed, {}", myId, reason);
        this.session = null;

        if (closeListener != null) {
            closeListener.accept(reason);
        }
    }

    @OnError
    public void onError(Throwable t) {
        LOG.warn("WS#{} On error: {}", myId, t);
        onClose(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "Crashed"));
    }

    public void close() {
        try {
            if (session != null) session.close();
        } catch (IOException ioe) {
            LOG.warn("WS#{}, IOE when closing", myId);
            onClose(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "Crashed"));
        }
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
    private Consumer<CloseReason> closeListener;
    public void setCLoseListener(Consumer<CloseReason> listener) {
        this.closeListener = listener;
    }
    public void setOpenListener(Runnable listener) {
        this.openListener = listener;
    }

    public void sendPacket(ByteString bin) {
        LOG.debug("WS#{} sending bin, {} bytes", myId, bin.size());
        try {
            session.getBasicRemote().sendBinary(ByteBuffer.wrap(bin.toByteArray()));
        } catch (IOException e) {
            LOG.warn("WS#{}, IOE when sending binary", myId, e);
            onClose(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "Crashed"));
        }
    }

    public void sendPacket(JSONObject json) {
        final String str = json.toString();
        LOG.debug("WS#{} sending text: '{}'", myId, str);
        try {
            session.getBasicRemote().sendText(str);
        } catch (IOException e) {
            LOG.warn("WS#{}, IOE when sending text", myId, e);
            onClose(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "Crashed"));
        }

    }
}
