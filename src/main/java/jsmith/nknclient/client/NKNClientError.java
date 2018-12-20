package jsmith.nknclient.client;

import com.google.protobuf.ByteString;

/**
 *
 */
public class NKNClientError extends Error {

    public NKNClientError(String message) {
        super(message);
    }

    public NKNClientError(String message, Throwable cause) {
        super(message, cause);
    }

    public static class MessageAckTimeout extends Error {

        public final ByteString messageID;

        public MessageAckTimeout(ByteString messageID) {
            this.messageID = messageID;
        }

    }

    public static class UnknownObjectType extends Error {

        public UnknownObjectType(String message) {
            super(message);
        }

    }

}
