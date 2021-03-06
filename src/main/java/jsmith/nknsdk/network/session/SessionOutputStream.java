package jsmith.nknsdk.network.session;

import com.google.protobuf.ByteString;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class SessionOutputStream extends OutputStream {


    private final Object bufferLock = new Object();
    private ByteString buffer = ByteString.EMPTY;

    private boolean autoFlushReq, flushReq;

    private final Session s;
    private final SessionHandler handler;
    private final AtomicInteger seqId = new AtomicInteger(0);
    SessionOutputStream(Session s, SessionHandler handler) {
        this.s = s;
        this.handler = handler;
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte)b}, 0, 1);
    }

    @Override
    public void write(@NotNull byte[] bytes, int offset, int length) throws IOException {
        if (s.isClosing) throw new IOException("Session is closing or closed, cannot send new data");

        synchronized (bufferLock) {
            buffer = buffer.concat(ByteString.copyFrom(bytes, offset, length));
            if (buffer.size() >= s.mtu) {
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

    long lastFlush = 0;
    void timedFlush() {
        if (System.currentTimeMillis() - lastFlush > 70) {
            try {
                flush();
                lastFlush = System.currentTimeMillis();
            } catch (IOException ignored) {}
        }
    }

    private final Object flushLock = new Object();
    private void doFlush() throws IOException {
        try {
            synchronized (flushLock) {
                if (!autoFlushReq && !flushReq) return;
                synchronized (bufferLock) {
                    while (buffer.size() >= s.mtu) {
                        s.sendQ.put(new Session.DataChunk(seqId.incrementAndGet(), buffer.substring(0, s.mtu)));
                        buffer = buffer.substring(s.mtu);
                    }
                    if (flushReq && !buffer.isEmpty()) {
                        s.sendQ.put(new Session.DataChunk(seqId.incrementAndGet(), buffer));
                        buffer = ByteString.EMPTY;
                    }
                }
                flushReq = false;
                autoFlushReq = false;
            }
            handler.waitForFlush(s);
        } catch (InterruptedException ie) {
            throw new IOException(ie);
        }
    }

    @Override
    public void close() {
        s.close();
    }

    public int getUnconfirmedSentBytesCount() {
        synchronized (bufferLock) {
            return (int)(s.sentBytesIntegral.get(s.latestSentSeqId) - s.sentBytesIntegral.get(s.latestConfirmedSeqId) + s.sendQ.stream().mapToInt(dc -> dc.data.size()).sum() + buffer.size());
        }
    }

}
