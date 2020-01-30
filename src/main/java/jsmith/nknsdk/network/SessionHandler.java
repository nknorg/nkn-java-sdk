package jsmith.nknsdk.network;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.network.proto.MessagesP;
import jsmith.nknsdk.utils.Crypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

/**
 *
 */
public class SessionHandler extends Thread {

    public static final int MAX_MTU = 1024;
    public static final int MAX_WIN_SIZE = 4 * 1024 * 1024;
    public static final int MAX_MULTICLIENTS = 16;
    public static final int DEFAULT_MULTICLIENTS = 8;

    private static final Logger LOG = LoggerFactory.getLogger(SessionHandler.class);


    private final ClientTunnel ct;
    public SessionHandler(ClientTunnel ct) {
        super("SessionHandler#" + ct.myId);
        this.ct = ct;
        start();
        // TODO daemon thread?
    }

    public Session dialSession(String destinationFullIdentifier) throws NKNClientException {
        return dialSession(destinationFullIdentifier, DEFAULT_MULTICLIENTS, null);
    }
    public Session dialSession(String destinationFullIdentifier, int multiclientCount, String[] targetPrefixes) throws NKNClientException {
        final int multiclients = Math.min(multiclientCount, MAX_MULTICLIENTS);
        ct.ensureMulticlients(multiclients);

        // TODO user cap multiclients

        ArrayList<String> prefixes;
        if (targetPrefixes != null) {
            prefixes = new ArrayList<>(targetPrefixes.length);
            Collections.addAll(prefixes, targetPrefixes);
        } else {
            prefixes = new ArrayList<>(multiclients + 1);
            prefixes.add("");
            for (int i = 0; i < multiclients; i++) {
                prefixes.add("__" + i + "__");
            }
        }

        final Session s = new Session(this, prefixes, multiclients, destinationFullIdentifier, ByteString.copyFrom(Crypto.nextRandom4B()), MAX_MTU, MAX_WIN_SIZE);
        activeSessions.put(new SessionKey(destinationFullIdentifier, s.sessionId), s);
        LOG.info("Dialing session");
        establishSession(s);
        return s;
    }

    private Function<Session, Boolean> acceptSession = null;
    public void onSessionRequest(Function<Session, Boolean> accept) {
        this.acceptSession = accept;
    }


    private final HashMap<SessionKey, Session> activeSessions = new HashMap<>();

    void onMessage(ClientMessageWorkers cmw, String fromRaw, ByteString sessionId, ByteString bytes) {
        String prefix = fromRaw.contains(".") ? fromRaw.substring(0, fromRaw.indexOf(".")) : "";
        String from = fromRaw;
        if (prefix.matches("^__\\d+__$")) {
            from = fromRaw.substring(fromRaw.indexOf(".") + 1);
        }
        final SessionKey sk = new SessionKey(from, sessionId);

        try {
            MessagesP.SessionData data = MessagesP.SessionData.parseFrom(bytes);

            if (data.getAckStartSeqCount() != data.getAckSeqCountCount()) {
                throw new InvalidProtocolBufferException("AckStartSeq does not have the same length as AckSeqCount");
            }

            Session s = activeSessions.get(sk);
            if (s != null) {
                if (s.isClosed) return;

                final int sequenceId = data.getSequenceId();
                final int ackSeqLength = data.getAckStartSeqCount();

                if (sequenceId == 0 && ackSeqLength == 0) { // Handshake request
                    if (!s.isEstablished) {
                        s.isEstablished = true; // TODO lock


                        final int mtu = data.getMtu();
                        final int winSize = data.getWindowSize();
                        s.mtu = Math.min(mtu, s.mtu);
                        s.winSize = Math.min(winSize, s.winSize);
                        s.prefixes = data.getIdentifierPrefixList();

                        s.ownMulticlients = Math.min(s.prefixes.size(), s.ownMulticlients);

                        for (int i = 0; i < s.ownMulticlients; i++) {
                            ct.multiclients.get(i).getAssociatedCM().trackWinSize(s.remoteIdentifier, ClientMessageWorkers.DEFAULT_INITIAL_CONNECTION_WINSIZE);
                        }

                        try {
                            ct.ensureMulticlients(s.ownMulticlients);
                        } catch (NKNClientException e) {
                            LOG.warn("Failed to create multiclients", e);
                        }

                        LOG.info("Session has been established");
                        if (s.onSessionEstablishedCb != null) {
                            s.onSessionEstablishedCalled = true;
                            s.onSessionEstablishedCb.run();
                        }
                    }
                } else if (data.getClose()) { // Close request
                    // TODO flush and close
                } else {
                    if (sequenceId != 0) {
                        s.onReceivedChunk(sequenceId, data.getData());
                    }
                    if (ackSeqLength > 0) {
                        for (int i = 0; i < ackSeqLength; i++) {
                            s.onReceivedAck(data.getAckStartSeq(i), data.getAckSeqCount(i));
                        }
                    }
                }

            } else {


                final int sequenceId = data.getSequenceId();
                final int ackSeqLength = data.getAckSeqCountCount() + data.getAckStartSeqCount();
                if (sequenceId == 0 && ackSeqLength == 0) { // Handshake request
                    final int mtu = data.getMtu();
                    final int winSize = data.getWindowSize();

                    s = new Session(this, data.getIdentifierPrefixList(), Math.min(DEFAULT_MULTICLIENTS, data.getIdentifierPrefixCount()), from, sessionId, Math.min(mtu, MAX_MTU), Math.min(winSize, MAX_WIN_SIZE));

                    activeSessions.put(sk, s);

                    if (acceptSession != null && acceptSession.apply(s)) {
                        LOG.info("Reply sent with session establishment confirmation");

                        try {
                            ct.ensureMulticlients(s.ownMulticlients);
                        } catch (NKNClientException e) {
                            LOG.warn("Failed to create multiclients", e);
                        }
                        establishSession(s);
                        for (int i = 0; i < s.ownMulticlients; i++) {
                            ct.multiclients.get(i).getAssociatedCM().trackWinSize(s.remoteIdentifier, ClientMessageWorkers.DEFAULT_INITIAL_CONNECTION_WINSIZE);
                        }
                        s.isEstablished = true;
                        if (s.onSessionEstablishedCb != null) {
                            s.onSessionEstablishedCalled = true;
                            s.onSessionEstablishedCb.run();
                        }
                    } else {
                        s.isClosed = true;
                    }
                }

            }
        } catch (InvalidProtocolBufferException e) {
            LOG.warn("Invalid session packet received", e);
        }
    }

    @Override
    public void run() {
        while (ct.running) { // TODO or buffers remaining
            for (Session s : activeSessions.values()) {
                // TODO multithreaded concurrent modification?
                final Iterator<Map.Entry<Session.DataChunk, Session.SentLog>> iterator = s.sentQ.entrySet().iterator();
                while (iterator.hasNext()) {
                    final Map.Entry<Session.DataChunk, Session.SentLog> sent = iterator.next();
                    if (System.currentTimeMillis() - sent.getValue().sentAt > sent.getValue().sentBy.getTrackedRto(s.remoteIdentifier)) {
                        sent.getValue().sentBy.onWinsizeAckTimeout(s.remoteIdentifier);
                        s.resendQ.add(sent.getKey());
                        iterator.remove();
                    }
                }
            }

            boolean remaining = true;
            while (remaining) { // Somewhat balance all active sessions, but don't wait if there is data to be send remaining
                // TODO available and not available connections
                remaining = false;
                for (Session s : activeSessions.values()) {
                    if (s.isEstablished) {
                        try {
                            int workerI = -1;
                            final ArrayList<Integer> availableMulticlients = new ArrayList<>(s.ownMulticlients);
                            for (int i = 0; i < s.ownMulticlients; i++) {
                                if (ct.multiclients.get(i).getAssociatedCM().isWinSizeAvailable(s.remoteIdentifier)) {
                                    availableMulticlients.add(i);
                                }
                            }
                            if (!availableMulticlients.isEmpty()) {
                                workerI = (int)(Math.random() * availableMulticlients.size());
                            }

                            if (workerI != -1) {
                                String chosenRemote = s.prefixes.get(workerI) + "." + s.remoteIdentifier;
                                if (chosenRemote.startsWith(".")) chosenRemote = chosenRemote.substring(1);
                                remaining |= flushDataChunk(s, ct.multiclients.get(workerI).getAssociatedCM(), chosenRemote);
                            }
                        } catch (InterruptedException ignored) {}
                    }
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {}
        }
    }

    private boolean flushDataChunk(Session s, ClientMessageWorkers chosenWorker, String chosenRemote) throws InterruptedException {
        // Assuming the worker is available
        synchronized (s.sendQ) {
            Session.DataChunk dataChunk = null;
            if (!s.resendQ.isEmpty()) {
                dataChunk = s.resendQ.take();
            } else if (!s.sendQ.isEmpty() && s.sentBytesIntegral.get(s.latestSentSeqId) + s.sendQ.peek().data.size() <= s.winSize) {
                dataChunk = s.sendQ.take();
            } else if (System.currentTimeMillis() - s.lastSentAck < 50 || s.pendingAcks.isEmpty()) {
                return false;
            }

            MessagesP.SessionData.Builder packetBuilder = MessagesP.SessionData.newBuilder()
                    .setSequenceId(0)
                    .setClose(false);

            if (dataChunk != null) {
                packetBuilder.setSequenceId(dataChunk.sequenceId);
                packetBuilder.setData(dataChunk.data);
            }

            Iterator<Session.AckBundle> acks = s.pendingAcks.iterator();
            for (int aI = 0; aI < 32 && acks.hasNext(); aI++) {
                final Session.AckBundle ack = acks.next();
                packetBuilder.addAckStartSeq(ack.startSeq);
                packetBuilder.addAckSeqCount(ack.count);
                acks.remove();
            }

            LOG.debug("Sending chunk #{} to {}", dataChunk == null ? "ACK" : dataChunk.sequenceId, chosenRemote);


            if (dataChunk != null) {
                chosenWorker.sendWinsizeTrackedPacket(chosenRemote);
                s.sentQ.put(dataChunk, new Session.SentLog(System.currentTimeMillis(), chosenWorker));
                if (s.latestSentSeqId + 1 == dataChunk.sequenceId) {
                    s.sentBytesIntegral.putIfAbsent(dataChunk.sequenceId, s.sentBytesIntegral.get(s.latestSentSeqId) + dataChunk.data.size());
                    s.latestSentSeqId = dataChunk.sequenceId;
                }
            }
            chosenWorker.sendMessageAsync(Collections.singletonList(chosenRemote), s.sessionId, MessagesP.PayloadType.SESSION, packetBuilder.build().toByteString());
            s.lastSentAck = System.currentTimeMillis();
            return (s.resendQ.size() + s.sendQ.size()) != 0 /* && still available win_size slot */;

        }
    }

    boolean flushDataChunk(Session s) throws InterruptedException {
        while (s.resendQ.isEmpty() && !s.sendQ.isEmpty() && s.sentBytesIntegral.get(s.latestSentSeqId) + s.sendQ.peek().data.size() <= s.winSize) {
            Thread.sleep(300); // Wait for remote winsize to open
            // TODO proper sleep and notify
        }

        int workerI = -1;
        while (workerI == -1) {
            final ArrayList<Integer> availableMulticlients = new ArrayList<>(s.ownMulticlients);
            for (int i = 0; i < s.ownMulticlients; i++) {
                if (ct.multiclients.get(i).getAssociatedCM().isWinSizeAvailable(s.remoteIdentifier)) {
                    availableMulticlients.add(i);
                }
            }
            if (!availableMulticlients.isEmpty()) {
                workerI = (int)(Math.random() * availableMulticlients.size());
            } else {
                Thread.sleep(300);
            }
        }

        final ClientMessageWorkers chosenWorker = ct.multiclients.get(workerI).getAssociatedCM();
        String chosenRemote = s.prefixes.get(workerI) + "." + s.remoteIdentifier;
        if (chosenRemote.startsWith(".")) chosenRemote = chosenRemote.substring(1);

        return flushDataChunk(s, chosenWorker, chosenRemote);
    }

    private void establishSession(Session s) {
        if (s.isClosed || s.isClosing || s.isEstablished) return;

        final ArrayList<String> myPrefixes = new ArrayList<>(s.ownMulticlients);
        for (int i = 0; i < s.ownMulticlients; i++) {
            myPrefixes.add("__" + i + "__");
        }

        MessagesP.SessionData data = MessagesP.SessionData.newBuilder()
                .setSequenceId(0)
                .addAllIdentifierPrefix(myPrefixes)
                .setMtu(s.mtu)
                .setWindowSize(s.winSize)
                .setClose(false)
                .build();

        final ByteString packet = data.toByteString();
        for (int i = 0; i < s.ownMulticlients && i < s.prefixes.size(); i ++) {
            String remote = s.prefixes.get(i) + "." + s.remoteIdentifier;
            if (remote.startsWith(".")) remote = remote.substring(1);
            ct.multiclients.get(i).getAssociatedCM().sendMessageAsync(Collections.singletonList(remote), s.sessionId, MessagesP.PayloadType.SESSION, packet);
        }
    }


    private static class SessionKey {
        private final String remote;
        private final ByteString messageId;
        SessionKey(String remote, ByteString messageId) {
            if (remote == null) throw new NullPointerException("Argument 'remote' is null");
            if (messageId == null) throw new NullPointerException("Arguments 'messageId' is null");
            this.remote = remote;
            this.messageId = messageId;
        }

        @Override
        public int hashCode() {
            return 13 * remote.hashCode() + 23 * messageId.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SessionKey)) return false;
            return remote.equals(((SessionKey) o).remote) && messageId.equals(((SessionKey) o).messageId);
        }
    }

}
