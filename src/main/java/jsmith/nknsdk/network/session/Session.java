package jsmith.nknsdk.network.session;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.network.ClientMessageWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public class Session {

    private static final Logger LOG = LoggerFactory.getLogger(Session.class);

    // TODO Not all status reads are properly synchronized. It should be fine though
    boolean isEstablished;
    boolean isBroken;
    boolean isClosing;
    boolean isClosed;
    boolean isClosedOutbound;

    final AtomicLong bytesRead = new AtomicLong(0);
    long lastSentBytesRead = 0;
    final AtomicLong remoteBytesRead = new AtomicLong(0);
    long lastSentBytesReadTime = 0;

    public final String remoteIdentifier;
    final ByteString sessionId;

    List<String> prefixes;
    int ownMulticlients;
    int mtu, winSize;

    final Object lock = new Object();

    private final SessionInputStream is;
    private final SessionOutputStream os;

    Session(SessionHandler handler, List<String> prefixes, int ownMulticlients, String remoteIdentifier, ByteString sessionId, int mtu, int winSize) {
        this.prefixes = prefixes;
        this.ownMulticlients = ownMulticlients;
        this.remoteIdentifier = remoteIdentifier;
        this.sessionId = sessionId;

        this.mtu = mtu;
        this.winSize = winSize;

        sentBytesIntegral.put(0, 0L);

        os = new SessionOutputStream(this, handler);
        is = new SessionInputStream(this);
    }

    void establishSession(List<String> prefixes, int mtu, int ownMulticlients, int winSize) {
        synchronized (this) {
            this.mtu = mtu;
            this.prefixes = prefixes;
            this.ownMulticlients = ownMulticlients;
            this.winSize = winSize;

            sendQ = new ArrayBlockingQueue<>(winSize / mtu + 16);
            resendQ = new PriorityBlockingQueue<>(winSize / mtu + 32, Comparator.comparingInt(j -> j.sequenceId));

            lastReceivedPacket = System.currentTimeMillis();

            isEstablished = true;
        }
    }

    Runnable onSessionEstablishedCb = null;
    boolean onSessionEstablishedCalled = false;
    public void onSessionEstablished(Runnable onSessionEstablished) {
        this.onSessionEstablishedCb = onSessionEstablished;
        if (isEstablished && !onSessionEstablishedCalled && onSessionEstablished != null) {
            onSessionEstablishedCalled = true;
            onSessionEstablished.run();
        }
    }
    Runnable onSessionBrokenCb = null;
    boolean onSessionBrokenCalled = false;
    long lastReceivedPacket = -1;
    public void onSessionBrokenTunnel(Runnable onSessionBrokenTunnel) {
        this.onSessionBrokenCb = onSessionBrokenTunnel;
        if (isBroken && !onSessionBrokenCalled && onSessionBrokenCb != null) {
            onSessionBrokenCalled = true;
            onSessionBrokenCb.run();
        }
    }

    public SessionInputStream getInputStream() {
        if (!isEstablished || isClosed) throw new IllegalStateException("The session is not active, cannot return stream");
        return is;
    }
    public SessionOutputStream getOutputStream() {
        if (!isEstablished || isClosed) throw new IllegalStateException("The session is not active, cannot return stream");
        return os;
    }


    // Outbound
    int latestConfirmedSeqId = 0;
    int latestSentSeqId = 0;
    BlockingQueue<DataChunk> sendQ;
    final HashMap<DataChunk, SentLog> sentQ = new HashMap<>();
    final HashMap<Integer, Long> sentBytesIntegral = new HashMap<>();
    BlockingQueue<DataChunk> resendQ;

    // Acks
    final ArrayList<AckBundle> pendingAcks = new ArrayList<>();


    void onReceivedAck(int startSeq, int count) {
        if (startSeq == latestConfirmedSeqId + 1) latestConfirmedSeqId = startSeq + count - 1;

        synchronized (sentQ) {
            sentQ.entrySet().removeIf(entry -> {
                boolean acked = entry.getKey().sequenceId >= startSeq && entry.getKey().sequenceId < startSeq + count;
                if (acked) {
                    entry.getValue().sentBy.onWinsizeAckReceived(remoteIdentifier, (int) (System.currentTimeMillis() - entry.getValue().sentAt));
                }
                return acked;
            });
            resendQ.removeIf(entry -> entry.sequenceId >= startSeq && entry.sequenceId < startSeq + count);

            if (sentQ.isEmpty()) {
                latestConfirmedSeqId = latestSentSeqId;
            } else {
                sentQ.keySet().stream().min(Comparator.comparingInt(dc -> dc.sequenceId)).ifPresent(dc -> latestConfirmedSeqId = dc.sequenceId - 1);
            }

            sentBytesIntegral.entrySet().removeIf(entry -> entry.getKey() < latestConfirmedSeqId);
        }
    }

    void onReceivedChunk(int sequenceId, ByteString data, ClientMessageWorker from) {
        if (is.onReceivedDataChunk(sequenceId, data)) {

            synchronized (pendingAcks) {
                // Check for appends
                int appendedI = -1;
                boolean within = false;
                for (int i = 0; i < pendingAcks.size(); i++) {
                    AckBundle ack = pendingAcks.get(i);
                    if (ack.worker != from) continue;

                    if (ack.startSeq + ack.count == sequenceId) {
                        ack.count += 1;
                        appendedI = i;
                        break;
                    }
                    if (ack.startSeq >= sequenceId && ack.startSeq + ack.count < sequenceId) {
                        within = true;
                        break;
                    }
                }
                // Check for prepends
                if (!within) {
                    int mergedI = -1;
                    boolean prepended = false;
                    for (int i = 0; i < pendingAcks.size(); i++) {
                        AckBundle ack = pendingAcks.get(i);
                        if (ack.worker != from) continue;

                        if (ack.startSeq - 1 == sequenceId) {
                            ack.startSeq -= 1;
                            ack.count += 1;
                            prepended = true;
                            if (appendedI != -1) {
                                AckBundle newAck = pendingAcks.get(appendedI);
                                newAck.count += ack.count - 1;
                                mergedI = i;
                            }
                            break;
                        }
                    }
                    if (mergedI != -1) pendingAcks.remove(mergedI);

                    if (appendedI == -1 && !prepended) {
                        pendingAcks.add(new AckBundle(from, sequenceId, 1));
                    }
                }
            }
        }
    }

    public void close() {
        synchronized (this) {
            if (isClosing) return;

            if (!isEstablished) {
                isClosing = true;
                isClosed = true;
                isClosedOutbound = true;
            } else {
                isClosing = true;
                try {
                    os.flush();
                } catch (IOException ignored) {}
            }
        }
    }

    static class AckBundle {
        final ClientMessageWorker worker;
        int startSeq;
        int count;
        AckBundle(ClientMessageWorker w, int startSeq, int count) {
            this.worker = w;
            this.startSeq = startSeq;
            this.count = count;
        }
    }

    static class DataChunk {
        final int sequenceId;
        final ByteString data;
        DataChunk(int sequenceId, ByteString data) {
            this.sequenceId = sequenceId;
            this.data = data;
        }
    }

    static class SentLog {
        final long sentAt;
        final ClientMessageWorker sentBy;
        SentLog(long sentAt, ClientMessageWorker sentBy) {
            this.sentAt = sentAt;
            this.sentBy = sentBy;
        }
    }
}
