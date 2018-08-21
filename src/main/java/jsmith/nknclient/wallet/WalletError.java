package jsmith.nknclient.wallet;

/**
 *
 */
public class WalletError extends Error {

    public WalletError(String message) {
        super(message);
    }

    public WalletError(String message, Throwable cause) {
        super(message, cause);
    }

}
