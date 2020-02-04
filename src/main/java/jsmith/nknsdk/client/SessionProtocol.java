package jsmith.nknsdk.client;

import jsmith.nknsdk.network.ClientTunnel;
import jsmith.nknsdk.network.session.Session;
import jsmith.nknsdk.network.session.SessionHandler;

import java.util.function.Function;

/**
 *
 */
public class SessionProtocol {

    private final SessionHandler handler;

    SessionProtocol(SessionHandler handler) {
        this.handler = handler;
    }

    public Session dialSession(String destinationFullIdentifier) throws NKNClientException {
        return handler.dialSession(destinationFullIdentifier, SessionHandler.DEFAULT_MULTICLIENTS, null);
    }

    public Session dialSession(String destinationFullIdentifier, int multiclientCount, String[] targetPrefixes) throws NKNClientException {
        return handler.dialSession(destinationFullIdentifier, multiclientCount, targetPrefixes);
    }

    public void onSessionRequest(Function<Session, Boolean> accept) {
        handler.onSessionRequest(accept);
    }

    // TODO ability to set multiclients count, mtu, and all other params on receiving session

    void close() {
        handler.close();
    }

}
