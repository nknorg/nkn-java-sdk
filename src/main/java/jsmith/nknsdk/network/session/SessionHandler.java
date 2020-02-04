package jsmith.nknsdk.network.session;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.network.ClientMessageWorker;
import jsmith.nknsdk.network.ClientTunnel;
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
    public static final int DEFAULT_MULTICLIENTS = 2;

    private static final Logger LOG = LoggerFactory.getLogger(SessionHandler.class);


    private final ClientTunnel ct;
    public SessionHandler(ClientTunnel ct, int id) {
        super("SessionHandler#" + id);
        this.ct = ct;
        start();
    }

    public Session dialSession(String destinationFullIdentifier, int multiclientCount, String[] targetPrefixes) throws NKNClientException {
        if (isClosing) throw new IllegalStateException("SessionHandler is in closed state, cannot dial session");

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

    public void onMessage(ClientMessageWorker cmw, String fromRaw, ByteString sessionId, ByteString bytes) {
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
                synchronized (s.lock) {
                    if (s.isClosed) return;

                    final int sequenceId = data.getSequenceId();
                    final int ackSeqLength = data.getAckStartSeqCount();

                    if (sequenceId == 0 && ackSeqLength == 0) { // Handshake request
                        if (!isClosing) {
                            if (!s.isEstablished) {
                                s.isEstablished = true;


                                final int mtu = data.getMtu();
                                final int winSize = data.getWindowSize();
                                s.mtu = Math.min(mtu, s.mtu);
                                s.winSize = Math.min(winSize, s.winSize);
                                s.prefixes = data.getIdentifierPrefixList();

                                s.ownMulticlients = Math.min(s.prefixes.size(), s.ownMulticlients);

                                for (int i = 0; i < s.ownMulticlients; i++) {
                                    ct.multiclients.get(i).getAssociatedCM().trackWinSize(s.remoteIdentifier, ClientMessageWorker.DEFAULT_INITIAL_CONNECTION_WINSIZE);
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
                        } else {
                            if (s.isEstablished) {
                                s.close();
                            }
                        }
                    } else if (data.getClose()) { // Close request
                        // TODO flush and close
                    } else {
                        if (sequenceId != 0) {
                            s.onReceivedChunk(sequenceId, data.getData(), cmw);
                        }
                        if (ackSeqLength > 0) {
                            for (int i = 0; i < ackSeqLength; i++) {
                                s.onReceivedAck(data.getAckStartSeq(i), data.getAckSeqCount(i));
                            }
                        }
                    }
                }

            } else {

                if (!isClosing) {
                    final int sequenceId = data.getSequenceId();
                    final int ackSeqLength = data.getAckSeqCountCount() + data.getAckStartSeqCount();
                    if (sequenceId == 0 && ackSeqLength == 0) { // Handshake request
                        final int mtu = data.getMtu();
                        final int winSize = data.getWindowSize();

                        s = new Session(this, data.getIdentifierPrefixList(), Math.min(DEFAULT_MULTICLIENTS, data.getIdentifierPrefixCount()), from, sessionId, Math.min(mtu, MAX_MTU), Math.min(winSize, MAX_WIN_SIZE));

                        synchronized (s.lock) {

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
                                    ct.multiclients.get(i).getAssociatedCM().trackWinSize(s.remoteIdentifier, ClientMessageWorker.DEFAULT_INITIAL_CONNECTION_WINSIZE);
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
                }

            }
        } catch (InvalidProtocolBufferException e) {
            LOG.warn("Invalid session packet received", e);
        }
    }


    private boolean isClosing = false;
    public void close() {
        if (!isClosing) {
            isClosing = true;
            activeSessions.values().forEach(Session::close);
            activeSessions.entrySet().removeIf(e -> e.getValue().isClosed);
        }
    }

    @Override
    public void run() {
        while (!isClosing || activeSessions.isEmpty()) {
            for (Session s : activeSessions.values()) {
                synchronized (s.sentQ) {
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
            }

            boolean remaining = true;
            while (remaining) { // Somewhat balance all active sessions, but don't wait if there is data to be send remaining
                remaining = false;
                for (Session s : activeSessions.values()) {
                    if (s.isEstablished) {
                        try {
                            final ArrayList<Integer> availableMulticlients = new ArrayList<>(s.ownMulticlients);
                            for (int i = 0; i < s.ownMulticlients; i++) {
                                if (ct.multiclients.get(i).getAssociatedCM().isWinSizeAvailable(s.remoteIdentifier)) {
                                    availableMulticlients.add(i);
                                }
                            }
                            if (!availableMulticlients.isEmpty()) {
                                int workerI = (int)(Math.random() * availableMulticlients.size());

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

    private boolean flushDataChunk(Session s, ClientMessageWorker chosenWorker, String chosenRemote) throws InterruptedException {
        Session.DataChunk dataChunk = null;
        if (!s.resendQ.isEmpty()) {
            dataChunk = s.resendQ.take();
        } else if (!s.sendQ.isEmpty() && s.sentBytesIntegral.get(s.latestSentSeqId) + s.sendQ.peek().data.size() <= s.winSize) {
            dataChunk = s.sendQ.take();
        } else if (s.pendingAcks.isEmpty()) {
            return false;
        }

        MessagesP.SessionData.Builder packetBuilder = MessagesP.SessionData.newBuilder()
                .setSequenceId(0)
                .setClose(false);

        if (dataChunk != null) {
            packetBuilder.setSequenceId(dataChunk.sequenceId);
            packetBuilder.setData(dataChunk.data);
        }

        boolean nonEmptyAck = false;
        synchronized (s.pendingAcks) {
            Iterator<Session.AckBundle> acks = s.pendingAcks.iterator();
            for (int aI = 0; aI < 32 && acks.hasNext(); aI++) {
                final Session.AckBundle ack = acks.next();
                if (ack.worker != chosenWorker) {
                    aI--;
                } else {
                    packetBuilder.addAckStartSeq(ack.startSeq);
                    packetBuilder.addAckSeqCount(ack.count);
                    nonEmptyAck = true;
                    acks.remove();
                }
            }
        }

        if (nonEmptyAck || dataChunk != null) {
            LOG.debug("Sending chunk #{} to {}", dataChunk == null ? "ACK" : dataChunk.sequenceId, chosenRemote);


            if (dataChunk != null) {
                chosenWorker.sendWinsizeTrackedPacket(chosenRemote);
                synchronized (s.sentQ) {
                    s.sentQ.put(dataChunk, new Session.SentLog(System.currentTimeMillis(), chosenWorker));
                    if (s.latestSentSeqId + 1 == dataChunk.sequenceId) {
                        s.sentBytesIntegral.putIfAbsent(dataChunk.sequenceId, s.sentBytesIntegral.get(s.latestSentSeqId) + dataChunk.data.size());
                        s.latestSentSeqId = dataChunk.sequenceId;
                    }
                }
            }
            chosenWorker.sendMessageAsync(Collections.singletonList(chosenRemote), s.sessionId, MessagesP.PayloadType.SESSION, packetBuilder.build().toByteString());
            return !s.resendQ.isEmpty() || (!s.sendQ.isEmpty() && s.sentBytesIntegral.get(s.latestSentSeqId) + s.sendQ.peek().data.size() <= s.winSize);
        } else {
            return false;
        }
    }

    void waitForFlush(Session s) throws InterruptedException {
        // TODO block properly, using wait and notify
        boolean workerAvailable = false;
        for (int i = 0; i < s.ownMulticlients; i++) {
            workerAvailable |= ct.multiclients.get(i).getAssociatedCM().isWinSizeAvailable(s.remoteIdentifier);
        }
        boolean winSizeAvailable = s.sentBytesIntegral.get(s.latestSentSeqId) + s.sendQ.stream().mapToInt(dc -> dc.data.size()).sum() <= s.winSize;

        while (!winSizeAvailable || !workerAvailable) {
            Thread.sleep(300);

            workerAvailable = false;
            for (int i = 0; i < s.ownMulticlients; i++) {
                workerAvailable |= ct.multiclients.get(i).getAssociatedCM().isWinSizeAvailable(s.remoteIdentifier);
            }

            winSizeAvailable = s.sentBytesIntegral.get(s.latestSentSeqId) + (s.sendQ.isEmpty() ? 0 : s.sendQ.peek().data.size()) <= s.winSize;
        }

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
