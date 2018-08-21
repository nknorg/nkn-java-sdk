package jsmith.nknclient.client;

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

}
