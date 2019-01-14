package jsmith.nknsdk.wallet;

/**
 *
 */
public class WalletException extends Exception {

    public WalletException(String message) {
        super(message);
    }

    public WalletException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class WalletCryptoError extends Error {

        public WalletCryptoError(String message, Throwable cause) {
            super(message, cause);
        }

    }

}
