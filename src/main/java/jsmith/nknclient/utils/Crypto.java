package jsmith.nknclient.utils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;

/**
 *
 */
public class Crypto {

    private static final Logger LOG = LoggerFactory.getLogger(Crypto.class);
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static byte[] sha256 (byte[] src) {
        try {

            MessageDigest md = MessageDigest.getInstance("SHA-256");

            md.update(src);
            return md.digest();

        } catch (NoSuchAlgorithmException e) {
            LOG.error("SHA-256 checksum failed", e);
            throw new CryptoError("SHA-256 is not supported - no such algorithm");
        }
    }

    public static byte[] doubleSha256 (byte[] src) {
        return sha256(sha256(src));
    }

    public static byte[] aesEncryptAligned(byte[] data, byte[] key, byte[] iv) {
        try {
            final Cipher c = Cipher.getInstance("AES/CBC/NoPadding", "BC");

            c.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(iv)
            );
            return c.doFinal(data);

        } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            throw new CryptoError("Could not encrypt block", e);
        }
    }

    public static byte[] aesDecryptAligned(byte[] data, byte[] key, byte[] iv) {
        try {
            final Cipher c = Cipher.getInstance("AES/CBC/NoPadding", "BC");

            c.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(iv)
            );
            return c.doFinal(data);

        } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            throw new CryptoError("Could not encrypt block", e);
        }
    }

    public static class CryptoError extends Error {

        public CryptoError(String message) {
            super(message);
        }

        public CryptoError(String message, Throwable cause) {
            super(message, cause);
        }

    }
}
