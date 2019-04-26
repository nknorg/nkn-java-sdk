package jsmith.nknsdk.utils;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
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

    private final static EdDSAParameterSpec ED25519 = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static byte[] sha256 (byte[] src) {
        try {

            MessageDigest md = MessageDigest.getInstance("SHA-256", "BC");

            md.update(src);
            return md.digest();

        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            LOG.error("SHA-256 checksum failed", e);
            throw new CryptoError("SHA-256 failed");
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

    public static byte[] sha256andSign(EdDSAPrivateKey key, byte[] data) {
        try {
            final Signature signatureEngine = new EdDSAEngine();
            signatureEngine.initSign(key);
            signatureEngine.update(sha256(data));

            return signatureEngine.sign();
        } catch (SignatureException | InvalidKeyException e) {
            throw new CryptoError("Could not sign block", e);
        }
    }
    public static boolean sha256andVerify(byte[] key, byte[] data, byte[] signature) {
        final EdDSAPublicKeySpec pubSpec = new EdDSAPublicKeySpec(key, ED25519);
        return sha256andVerify(new EdDSAPublicKey(pubSpec), data, signature);
    }
    public static boolean sha256andVerify(EdDSAPublicKey key, byte[] data, byte[] signature) {
        try {
            final Signature signatureEngine = new EdDSAEngine();
            signatureEngine.initVerify(key);
            signatureEngine.update(sha256(data));

//            if (signature.length == 64) { // Assuming raw encoding
//                final boolean extendR = signature[0] < 0;
//                final boolean extendS = signature[32] < 0;
//
//                final byte[] der = new byte[2 + 2 + 32 + (extendR ? 1 : 0) + 2 + 32 + (extendS ? 1 : 0)];
//                der[0] = 0x30;
//                der[1] = (byte)(2 + 32 + (extendR ? 1 : 0) + 2 + 32 + (extendS ? 1 : 0));
//
//                der[2] = 0x02;
//                der[3] = (byte)(32 + (extendR ? 1 : 0));
//                System.arraycopy(signature, 0, der, 4 + (extendR ? 1 : 0), 32);
//
//                der[der[3] + 4] = 0x02;
//                der[der[3] + 5] = (byte)(32 + (extendS ? 1 : 0));
//                System.arraycopy(signature, 32, der, der[3] + 6 + (extendS ? 1 : 0), 32);
//
//                return ecdsaVerify.verify(der);
//            } else { // Assuming DER encoding
//                return ecdsaVerify.verify(signature);
//            }
            return signatureEngine.verify(signature);

        } catch (SignatureException | InvalidKeyException e) {
            throw new CryptoError("Could not verify block", e);
        }
    }

    private static final SecureRandom randomId_sr = new SecureRandom();
    public static byte[] nextRandom32B() {
        final byte[] id = new byte[32];
        randomId_sr.nextBytes(id);
        return id;
    }
    public static byte[] nextRandom16B() {
        final byte[] id = new byte[16];
        randomId_sr.nextBytes(id);
        return id;
    }
    public static byte[] nextRandom4B() {
        final byte[] id = new byte[16];
        randomId_sr.nextBytes(id);
        return id;
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
