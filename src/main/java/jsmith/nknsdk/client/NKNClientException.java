package jsmith.nknsdk.client;

import com.google.protobuf.ByteString;

/**
 *
 */
public class NKNClientException extends Exception {

    public NKNClientException(String message) {
        super(message);
    }

    public NKNClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class MessageAckTimeout extends NKNClientException {

        public final ByteString messageID;

        public MessageAckTimeout(ByteString messageID) {
            super("");
            this.messageID = messageID;
        }

    }

    public static class UnknownObjectType extends Error {

        public UnknownObjectType(String message) {
            super(message);
        }

    }

}
