package jsmith.nknsdk.utils;

import com.google.protobuf.ByteString;

import java.util.Objects;

/**
 *
 */
public class EncodeUtils {

    public static ByteString encodeUint64(long l) {
        return ByteString.copyFrom(new byte[] {
                (byte) ((l & 0xFF)),
                (byte) ((l & 0xFF00) >> 8),
                (byte) ((l & 0xFF0000) >> 16),
                (byte) ((l & 0xFF000000) >> 24),

                (byte) ((l & 0xFF00000000L) >> 32),
                (byte) ((l & 0xFF0000000000L) >> 40),
                (byte) ((l & 0xFF000000000000L) >> 48),
                (byte) ((l & 0xFF00000000000000L) >> 56),
        });
    }
    public static ByteString encodeUint32(int i) {
        return ByteString.copyFrom(new byte[] {
                (byte) ((i & 0xFF)),
                (byte) ((i & 0xFF00) >> 8),
                (byte) ((i & 0xFF0000) >> 16),
                (byte) ((i & 0xFF000000) >> 24)
        });
    }
    public static ByteString encodeUint16(int i) {
        return ByteString.copyFrom(new byte[] {
                (byte) ((i & 0xFF)),
                (byte) ((i & 0xFF00) >> 8)
        });
    }
    public static ByteString encodeUint8(int i) {
        return ByteString.copyFrom(new byte[] {
                (byte) (i & 0xFF)
        });
    }
    public static ByteString encodeUint(long n) {

        if (n >= 0) {
            if (n < 0xfd) {
                return encodeUint8((int) n);
            } else if (n <= 0xffff) {
                return ByteString.copyFrom(new byte[] {(byte) 0xfd}).concat(encodeUint16((int)n));
            } else if (n <= 0xffffffffL) {
                return ByteString.copyFrom(new byte[] {(byte) 0xfe}).concat(encodeUint32((int)n));
            } else {
                return ByteString.copyFrom(new byte[] {(byte) 0xff}).concat(encodeUint64(n));
            }
        } else {
            return ByteString.copyFrom(new byte[] {(byte) 0xff}).concat(encodeUint64(n));
        }
    }
    public static ByteString encodeBytes(ByteString bytes) {
        return Objects.requireNonNull(encodeUint(bytes.size())).concat(bytes);
    }

}
