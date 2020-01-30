package jsmith.nknsdk.network;

import com.google.protobuf.ByteString;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class Session {

    private static final Logger LOG = LoggerFactory.getLogger(Session.class);

    private final SessionHandler handler;

    boolean isEstablished;
    boolean isClosing;
    boolean isClosed;

    final String remoteIdentifier;
    final ByteString sessionId;

    List<String> prefixes;
    int ownMulticlients;
    int mtu, winSize;


    private final InputStream is = new InputStream() {
        private ByteString buffer = ByteString.EMPTY;
        private final Object bufferLock = new Object();

        @Override
        public int read(@NotNull byte[] bytes, int offset, int length) throws IOException {
            if (length <= 0) return 0;
            if (offset < 0 || offset >= bytes.length) throw new IndexOutOfBoundsException("Offset is outside of bounds (" + offset + ")");
            length = Math.max(length, bytes.length - offset);

            int red = 0;
            synchronized (bufferLock) {
                if (buffer.size() > 0) {
                    int size = Math.min(buffer.size(), length);
                    buffer.copyTo(bytes, 0, offset, size);
                    red += size;
                    buffer = buffer.substring(0, red);
                }
            }
            if (red == 0 && receivedAvailableChunks.get() == 0) {
                // TODO wait for data
                // Or return EOF
            }
            while (receivedAvailableChunks.get() > 0 && red < length) {
                receivedAvailableChunks.decrementAndGet();
                int first = firstReceiveSequenceIdAvailable.incrementAndGet();
                final ByteString data = receivedChunks.remove(first);

                int size = Math.min(data.size(), length - red);
                data.copyTo(bytes, 0, offset + red, size);

                if (size < data.size()) {
                    synchronized (bufferLock) {
                        buffer = buffer.concat(data.substring(size));
                    }
                }
                red += size;
            }

            return red;
        }

        @Override
        public int available() throws IOException {
            return buffer.size(); // TODO readlock and availability in chunks
        }

        @Override
        public int read() throws IOException {
            final byte[] buff = new byte[1];
            int red;
            do {
                red = read(buff, 0, 1);
                if (red == -1) return -1;
            } while (red != 1);
            return buff[0] & 0xFF;
        }
    };
    private final OutputStream os = new OutputStream() {
        private final Object bufferLock = new Object();
        private ByteString buffer = ByteString.EMPTY;

        private boolean autoFlushReq, flushReq;

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte)b}, 0, 1);
        }

        @Override
        public void write(@NotNull byte[] bytes, int offset, int length) throws IOException {
            synchronized (bufferLock) {
                buffer = buffer.concat(ByteString.copyFrom(bytes, offset, length));
                if (buffer.size() >= mtu) {
                    autoFlushReq = true;
                }
            }
            doFlush();
        }

        @Override
        public void flush() throws IOException {
            flushReq = true;
            doFlush();
        }

        private final Object flushLock = new Object();
        private void doFlush() throws IOException {
            try {
                synchronized (flushLock) {
                    if (!autoFlushReq && !flushReq) return;
                    synchronized (bufferLock) {
                        while (buffer.size() >= mtu) {
                            sendQ.put(new DataChunk(sendSequenceId.incrementAndGet(), buffer.substring(0, mtu)));
                            buffer = buffer.substring(mtu);
                        }
                        if (flushReq && !buffer.isEmpty()) {
                            sendQ.put(new DataChunk(sendSequenceId.incrementAndGet(), buffer));
                            buffer = ByteString.EMPTY;
                        }
                    }
                    flushReq = false;
                    autoFlushReq = false;
                }
                handler.flushDataChunk(Session.this);
            } catch (InterruptedException ie) {
                throw new IOException(ie);
            }
        }

        // TODO flush and close
    };

    Session(SessionHandler handler, List<String> prefixes, int ownMulticlients, String remoteIdentifier, ByteString sessionId, int mtu, int winSize) {
        this.handler = handler;
        this.prefixes = prefixes;
        this.ownMulticlients = ownMulticlients;
        this.remoteIdentifier = remoteIdentifier;
        this.sessionId = sessionId;

        this.mtu = mtu;
        this.winSize = winSize;

        sentBytesIntegral.put(0, 0);
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

    public InputStream getInputStream() {
        // if is not established or is closing or closed, throw an error
        return is;
    }
    public OutputStream getOutputStream() {
        // if is not established or is closing or closed, throw an error
        return os;
    }


    // Inbound
    private final AtomicInteger firstReceiveSequenceIdAvailable = new AtomicInteger(0); // TODo the tuple has to be atomic, not each separately
    private final AtomicInteger receivedAvailableChunks = new AtomicInteger(0);
    private final HashMap<Integer, ByteString> receivedChunks = new HashMap<>();


    // Outbound
    private final AtomicInteger sendSequenceId = new AtomicInteger(0);
    private int latestConfirmedSeqId = 0;
    int latestSentSeqId = 0;
    final BlockingQueue<DataChunk> sendQ = new ArrayBlockingQueue<>(100); // TODO Q sizes
    final HashMap<DataChunk, SentLog> sentQ = new HashMap<>();
    final HashMap<Integer, Integer> sentBytesIntegral = new HashMap<>();
    final BlockingQueue<DataChunk> resendQ = new PriorityBlockingQueue<>(100, Comparator.comparingInt(j -> j.sequenceId));

    // Acks
    final ArrayList<AckBundle> pendingAcks = new ArrayList<>();


    void onReceivedAck(int startSeq, int count) {
        // TODO thread safety
        if (startSeq == latestConfirmedSeqId + 1) latestConfirmedSeqId = startSeq + count - 1;

        sentQ.entrySet().removeIf(entry -> {
            boolean acked = entry.getKey().sequenceId >= startSeq && entry.getKey().sequenceId < startSeq + count;
            if (acked) {
                entry.getValue().sentBy.onWinsizeAckReceived(remoteIdentifier, (int)(System.currentTimeMillis() - entry.getValue().sentAt));
            }
            return acked;
        });
        resendQ.removeIf(entry -> entry.sequenceId >= startSeq && entry.sequenceId < startSeq + count);

        if (sentQ.isEmpty()) {
            latestConfirmedSeqId = latestSentSeqId;
        } else {
            sentQ.keySet().stream().min(Comparator.comparingInt(dc -> dc.sequenceId)).ifPresent(dc -> latestConfirmedSeqId = dc.sequenceId - 1);
        }
        System.out.println("Latest confirmed: " + latestConfirmedSeqId);
        int start = sentBytesIntegral.get(latestConfirmedSeqId);
        final Iterator<Map.Entry<Integer, Integer>> iterator = sentBytesIntegral.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<Integer, Integer> entry = iterator.next();
            if (entry.getKey() < latestConfirmedSeqId) {
                iterator.remove();
            } else {
                entry.setValue(entry.getValue() - start);
            }
        }
    }

    void onReceivedChunk(int sequenceId, ByteString data, ClientMessageWorker from) {
        LOG.debug("Received chunk, seq {}", sequenceId);
        receivedChunks.put(sequenceId, data);
        if (sequenceId == firstReceiveSequenceIdAvailable.get() + receivedAvailableChunks.get() + 1) {
            receivedAvailableChunks.incrementAndGet();

            int sid = sequenceId + 1;
            while (receivedChunks.containsKey(sid)) {
                receivedAvailableChunks.incrementAndGet();
                sid ++;
            }
        }

        // Check for appends
        int appendedI = -1;
        boolean within = false;
        for (int i = 0; i < pendingAcks.size(); i ++) {
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

        // TODO concurrent modification Ex
        for (AckBundle ack : pendingAcks) {
            System.out.println("Pending ack: " + ack.startSeq + " -> (" + ack.count + ")");
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
