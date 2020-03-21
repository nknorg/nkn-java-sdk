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
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

/**
 *
 */
public class SessionHandler extends Thread {

    public static final int MAX_MTU = 1024;
    public static final int MAX_WIN_SIZE = 4 * 1024 * 1024;
    public static final int MAX_MULTICLIENTS = 16;
    public static final int DEFAULT_MULTICLIENTS = 4;

    private static final Logger LOG = LoggerFactory.getLogger(SessionHandler.class);


    private final ClientTunnel ct;
    public SessionHandler(ClientTunnel ct, int id) {
        super("SessionHandler#" + id);
        this.ct = ct;
        start();
    }

    public Session dialSession(String destinationFullIdentifier, int multiclientsCount, String[] targetPrefixes, int maxMtu, int maxWindowSize) throws NKNClientException {
        if (isClosing) throw new IllegalStateException("SessionHandler is in closed state, cannot dial session");

        final int multiclients = Math.min(multiclientsCount, MAX_MULTICLIENTS);
        ct.ensureMulticlients(multiclients);

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

        final Session s = new Session(this, prefixes, multiclients, destinationFullIdentifier, ByteString.copyFrom(Crypto.nextRandom4B()), maxMtu, maxWindowSize);
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

    private int preferredMtu = MAX_MTU, preferredMulticlients = DEFAULT_MULTICLIENTS, preferredWinSize = MAX_WIN_SIZE;
    public void setIncomingPreferredMtu(int preferredMtu) {
        this.preferredMtu = preferredMtu;
    }
    public void setIncomingPreferredMulticlients(int preferredMulticlients) {
        this.preferredMulticlients = preferredMulticlients;
    }
    public void setIncomingPreferredWinSize(int preferredWinSize) {
        this.preferredWinSize = preferredWinSize;
    }

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

                    if (data.getHandshake()) {
                        if (!isClosing) {
                            if (!s.isEstablished) {

                                try {
                                    s.prefixes = data.getClientIdsList();
                                    ct.ensureMulticlients(Math.min(s.prefixes.size(), s.ownMulticlients));
                                } catch (NKNClientException e) {
                                    LOG.warn("Failed to create multiclients", e);
                                }
                                for (int i = 0; i < s.ownMulticlients; i++) {
                                    ct.multiclients.get(i).getAssociatedCM().trackWinSize(s.remoteIdentifier, ClientMessageWorker.DEFAULT_INITIAL_CONNECTION_WINSIZE);
                                }

                                final int mtu = data.getMtu();
                                final int winSize = data.getWindowSize();
                                s.establishSession(
                                        data.getClientIdsList(),
                                        Math.min(mtu, s.mtu),
                                        Math.min(s.prefixes.size(), s.ownMulticlients),
                                        Math.min(winSize, s.winSize)
                                );


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
                    } else {
                        final int sequenceId = data.getSequenceId();
                        final int ackSeqLength = data.getAckStartSeqCount();
                        final long bytesRead = data.getBytesRead();

                        if (sequenceId != 0) {
                            s.onReceivedChunk(sequenceId, data.getData(), cmw);
                        }
                        if (ackSeqLength > 0) {
                            for (int i = 0; i < ackSeqLength; i++) {
                                s.onReceivedAck(data.getAckStartSeq(i), data.getAckSeqCount(i));
                            }
                        }
                        s.remoteBytesRead.updateAndGet(br -> Math.max(bytesRead, br));
                        if (data.getClose()) { // Close request
                            LOG.debug("Received a close packet");
                            s.close();
                            s.getInputStream().sessionClosed();
                            if (s.isClosedOutbound) {
                                s.isClosed = true;
                                if (isClosing) activeSessions.remove(sk);
                            }
                        }
                    }
                }

            } else {

                if (!isClosing) {
                    if (data.getHandshake()) {
                        final int mtu = data.getMtu();
                        final int winSize = data.getWindowSize();

                        s = new Session(this, data.getClientIdsList(), Math.min(preferredMulticlients, data.getClientIdsCount()), from, sessionId, mtu, winSize);

                        synchronized (s.lock) {

                            activeSessions.put(sk, s);

                            if (acceptSession != null && acceptSession.apply(s)) {
                                LOG.info("Reply sent with session establishment confirmation");

                                try {
                                    ct.ensureMulticlients(s.ownMulticlients);
                                } catch (NKNClientException e) {
                                    LOG.warn("Failed to create multiclients", e);
                                }
                                for (int i = 0; i < s.ownMulticlients; i++) {
                                    ct.multiclients.get(i).getAssociatedCM().trackWinSize(s.remoteIdentifier, ClientMessageWorker.DEFAULT_INITIAL_CONNECTION_WINSIZE);
                                }
                                establishSession(s);
                                s.establishSession(s.prefixes, Math.min(s.mtu, preferredMtu), Math.min(Math.min(MAX_MULTICLIENTS, preferredMulticlients), s.ownMulticlients), Math.min(s.winSize, preferredWinSize));
                                if (s.onSessionEstablishedCb != null) {
                                    s.onSessionEstablishedCalled = true;
                                    s.onSessionEstablishedCb.run();
                                }
                            } else {
                                s.isClosed = true;
                                s.isClosedOutbound = true;
                                if (isClosing) activeSessions.remove(sk);
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
    private CountDownLatch closingLatch = new CountDownLatch(1);
    public void close() throws InterruptedException {
        if (!isClosing) {
            isClosing = true;
            activeSessions.values().forEach(Session::close);
            activeSessions.entrySet().removeIf(e -> e.getValue().isClosed);
        }
        closingLatch.await();
    }

    @Override
    public void run() {
        while (!isClosing || !activeSessions.isEmpty()) {
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
                    if (s.isEstablished && !s.isClosedOutbound) {
                        try {
                            final ArrayList<Integer> availableMulticlients = new ArrayList<>(s.ownMulticlients);
                            for (int i = 0; i < s.ownMulticlients; i++) {
                                if (ct.multiclients.get(i).getAssociatedCM().isWinSizeAvailable(s.remoteIdentifier)) {
                                    availableMulticlients.add(i);
                                }
                            }
                            if (!availableMulticlients.isEmpty()) {
                                int workerI = availableMulticlients.get((int)(Math.random() * availableMulticlients.size()));

                                String chosenRemote = s.prefixes.get(workerI) + "." + s.remoteIdentifier;
                                if (chosenRemote.startsWith(".")) chosenRemote = chosenRemote.substring(1);
                                remaining |= flushDataChunk(s, ct.multiclients.get(workerI).getAssociatedCM(), chosenRemote);

                                if (s.lastSentBytesRead == s.bytesRead.get() && System.currentTimeMillis() - s.lastSentBytesReadTime < 5000) {
                                    long bytesRead = s.bytesRead.get();
                                    MessagesP.SessionData.Builder packetBuilder = MessagesP.SessionData.newBuilder()
                                            .setSequenceId(0)
                                            .setBytesRead(bytesRead)
                                            .setClose(false);

                                    for (int worker : availableMulticlients) {

                                        chosenRemote = s.prefixes.get(worker) + "." + s.remoteIdentifier;
                                        if (chosenRemote.startsWith(".")) chosenRemote = chosenRemote.substring(1);
                                        if (s.lastSentBytesRead < bytesRead) {
                                            ct.multiclients.get(worker).getAssociatedCM().sendMessageAsync(
                                                    Collections.singletonList(chosenRemote), s.sessionId, MessagesP.PayloadType.SESSION,
                                                    packetBuilder.build().toByteString());
                                        }
                                    }
                                    s.lastSentBytesRead = bytesRead;
                                    s.lastSentBytesReadTime = System.currentTimeMillis();
                                }
                            }

                        } catch (InterruptedException ignored) {}
                    }
                    if (s.isClosing && !s.isClosedOutbound && s.sentBytesIntegral.get(s.latestSentSeqId) - s.sentBytesIntegral.get(s.latestConfirmedSeqId) + s.sendQ.stream().mapToInt(dc -> dc.data.size()).sum() == 0) {
                        MessagesP.SessionData closePacket = MessagesP.SessionData.newBuilder()
                                .setSequenceId(0)
                                .setClose(true)
                                .build();

                        LOG.debug("Sending a close message, outbound Q is empty");
                        for (int i = 0; i < s.ownMulticlients; i++) {
                            String chosenRemote = s.prefixes.get(i) + "." + s.remoteIdentifier;
                            if (chosenRemote.startsWith(".")) chosenRemote = chosenRemote.substring(1);
                            ct.multiclients.get(i).getAssociatedCM().sendMessageAsync(
                                    Collections.singletonList(chosenRemote),
                                    s.sessionId,
                                    MessagesP.PayloadType.SESSION,
                                    closePacket.toByteString()
                            );
                        }
                        s.isClosedOutbound = true;
                        if (s.getInputStream().isClosedInbound) {
                            s.isClosed = true;
                            if (isClosing) activeSessions.remove(new SessionKey(s.remoteIdentifier, s.sessionId));
                        }
                    }
                }
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException ignored) {}
        }
        closingLatch.countDown();
    }

    private boolean flushDataChunk(Session s, ClientMessageWorker chosenWorker, String chosenRemote) throws InterruptedException {
        Session.DataChunk dataChunk = null;
        if (!s.resendQ.isEmpty()) {
            dataChunk = s.resendQ.take();
        } else if (!s.sendQ.isEmpty() && s.sentBytesIntegral.get(s.latestSentSeqId) - s.remoteBytesRead.get() + s.sendQ.peek().data.size() <= s.winSize) {
            dataChunk = s.sendQ.take();
        } else if (s.pendingAcks.isEmpty()) {
            return false;
        }

        long bytesRead = s.bytesRead.get();
        MessagesP.SessionData.Builder packetBuilder = MessagesP.SessionData.newBuilder()
                .setSequenceId(0)
                .setBytesRead(bytesRead)
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
            s.lastSentBytesRead = bytesRead;
            s.lastSentBytesReadTime = System.currentTimeMillis();
            return !s.resendQ.isEmpty() || (!s.sendQ.isEmpty() && s.sentBytesIntegral.get(s.latestSentSeqId) - s.remoteBytesRead.get() + s.sendQ.peek().data.size() <= s.winSize);
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

        boolean winSizeAvailable = s.sentBytesIntegral.get(s.latestSentSeqId) - s.remoteBytesRead.get() + s.sendQ.stream().mapToInt(dc -> dc.data.size()).sum() <= s.winSize;

        while (!winSizeAvailable || !workerAvailable) {
            Thread.sleep(300);

            workerAvailable = false;
            for (int i = 0; i < s.ownMulticlients; i++) {
                workerAvailable |= ct.multiclients.get(i).getAssociatedCM().isWinSizeAvailable(s.remoteIdentifier);
            }

            winSizeAvailable = s.sentBytesIntegral.get(s.latestSentSeqId) - s.remoteBytesRead.get() + s.sendQ.stream().mapToInt(dc -> dc.data.size()).sum() <= s.winSize;
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
                .setHandshake(true)
                .addAllClientIds(myPrefixes)
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
        private final ByteString sessionId;
        SessionKey(String remote, ByteString sessionId) {
            if (remote == null) throw new NullPointerException("Argument 'remote' is null");
            if (sessionId == null) throw new NullPointerException("Arguments 'sessionId' is null");
            this.remote = remote;
            this.sessionId = sessionId;
        }

        @Override
        public int hashCode() {
            return 13 * remote.hashCode() + 23 * sessionId.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SessionKey)) return false;
            return remote.equals(((SessionKey) o).remote) && sessionId.equals(((SessionKey) o).sessionId);
        }
    }

}
