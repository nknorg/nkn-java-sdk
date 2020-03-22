package jsmith.nknsdk.client;

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
        return handler.dialSession(destinationFullIdentifier, SessionHandler.DEFAULT_MULTICLIENTS, null, SessionHandler.MAX_MTU, SessionHandler.MAX_WIN_SIZE);
    }

    public Session dialSession(String destinationFullIdentifier, int multiclients, String[] targetPrefixes, int maxMtu, int maxWindowSize) throws NKNClientException {
        return handler.dialSession(destinationFullIdentifier, multiclients, targetPrefixes, maxMtu, maxWindowSize);
    }

    public void onSessionRequest(Function<Session, Boolean> accept) throws NKNClientException {
        handler.onSessionRequest(accept);
    }

    public void setIncomingPreferredMtu(int preferredMtu) {
        handler.setIncomingPreferredMtu(preferredMtu);
    }
    public void setIncomingPreferredMulticlients(int preferredMulticlients) {
        handler.setIncomingPreferredMulticlients(preferredMulticlients);
    }
    public void setIncomingPreferredWinSize(int preferredWinSize) {
        handler.setIncomingPreferredWinSize(preferredWinSize);
    }


    void close() throws InterruptedException {
        handler.close();
    }

}
