package jsmith.nknsdk.network.session;

import com.google.protobuf.ByteString;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class SessionInputStream extends InputStream {

    private ByteString buffer = ByteString.EMPTY;
    private final Object bufferLock = new Object();
    private final AtomicInteger lastSequenceIdInBuffer = new AtomicInteger(0);

    private final Session s;
    SessionInputStream(Session s) {
        this.s = s;
    }

    @Override
    public int read(@NotNull byte[] bytes, int offset, int length) throws IOException {
        if (length <= 0) return 0;
        if (offset < 0 || offset >= bytes.length) throw new IndexOutOfBoundsException("Offset is outside of bounds (" + offset + ")");
        length = Math.max(length, bytes.length - offset);

        synchronized (bufferLock) {
            while (buffer.size() == 0) {
                if (s.isClosed) return -1;
                try {
                    bufferLock.wait();
                } catch (InterruptedException e) {
                    throw new IOException("Thread interrupted while waiting for data", e);
                }
            }

            int size = Math.min(buffer.size(), length);
            buffer.copyTo(bytes, 0, offset, size);
            buffer = buffer.substring(size);
            return size;
        }
    }

    @Override
    public int available() {
        synchronized (bufferLock) {
            return buffer.size();
        }
    }

    @Override
    public int read() throws IOException {
        final byte[] buff = new byte[1];
        if (read(buff, 0, 1) == -1) return -1;
        return buff[0] & 0xFF;
    }

    @Override
    public void close() {
        s.close();
    }

    void sessionClosed() {
        synchronized (bufferLock) {
            bufferLock.notifyAll();
        }
        // TODO call the sessionClosed method from somewhere
    }

    private final ConcurrentHashMap<Integer, ByteString> receivedChunks = new ConcurrentHashMap<>();
    void onReceivedDataChunk(int sequenceId, ByteString data) {
        if (sequenceId - 1 == lastSequenceIdInBuffer.get()) {
            synchronized (bufferLock) {
                if (sequenceId - 1 == lastSequenceIdInBuffer.get()) {
                    int sid = sequenceId;
                    ByteString nextData = data;

                    while (nextData != null) {
                        buffer = buffer.concat(nextData);
                        lastSequenceIdInBuffer.set(sid);
                        sid ++;

                        nextData = receivedChunks.remove(sid);
                    }
                }

                bufferLock.notifyAll();
            }
        }

        final int lastSid = lastSequenceIdInBuffer.get();
        if (sequenceId > lastSid) {
            receivedChunks.put(sequenceId, data);
        }
        receivedChunks.keySet().removeIf(sid -> sid <= lastSid);
    }
}
