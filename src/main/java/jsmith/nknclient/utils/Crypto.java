package jsmith.nknclient.utils;

import org.bouncycastle.crypto.digests.RIPEMD160Digest;
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

    public static byte[] r160 (byte[] src) {
        final RIPEMD160Digest r160Digest = new RIPEMD160Digest();
        r160Digest.update(src, 0, src.length);
        final byte[] r160 = new byte[r160Digest.getDigestSize()];
        r160Digest.doFinal(r160, 0);

        return r160;
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
