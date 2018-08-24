package jsmith.nknclient.wallet;

import com.darkyen.dave.WebbException;
import jsmith.nknclient.Const;
import jsmith.nknclient.utils.Base58;
import jsmith.nknclient.utils.HttpApi;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.spec.ECGenParameterSpec;

/**
 *
 */
public class Wallet {

    private static final Logger LOG = LoggerFactory.getLogger(Wallet.class);
    private Wallet() {}
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private KeyPair keyPair = null;

    public static Wallet createNew() {
        final Wallet w = new Wallet();

        try {
            ECGenParameterSpec ecGenSpec = new ECGenParameterSpec("secp256r1");
            KeyPairGenerator g = KeyPairGenerator.getInstance("ECDSA", "BC");
            g.initialize(ecGenSpec, new SecureRandom());

            w.keyPair = g.generateKeyPair();

        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            LOG.error("Couldn't generate wallet", e);
            throw new WalletError("Could not generate wallet", e);
        }

        return w;
    }

//    public static Wallet load(InputStream is, byte[] key) {
//
//    }
//
//    public void save(OutputStream os, byte[] key) {
//
//    }

    public BigInteger queryBalance() {
        return queryBalance(Const.BOOTSTRAP_NODES_RPC);
    }
    public BigInteger queryBalance(InetSocketAddress bootstrapNodesRPC[]) {
        // Choose one node using round robin

        int bootstrapNodeIndex = (int)(Math.random() * bootstrapNodesRPC.length);
        InetSocketAddress bootstrapNodeRpc = bootstrapNodesRPC[bootstrapNodeIndex];
        int retries = Const.RETRIES;
        BigInteger result;
        WebbException error;
        do {
            try {
                result = HttpApi.getUTXO(bootstrapNodeRpc, this, Const.BALANCE_ASSET_ID);
                return result;
            } catch (WebbException e) {
                error = e;
                retries --;
                LOG.warn("Query balance RPC request failed, remaining retries: {}", retries);
            } catch (WalletError e) {
                LOG.warn("Failed to query balance", e);
                throw e;
            }
        } while (retries >= 0);

        throw new WalletError("Failed to query balance", error);
    }

    public String getPublicKeyAsHexString() {
        assert keyPair != null : "KeyPair is null, this should never happen";

        final BCECPublicKey pub = (BCECPublicKey)keyPair.getPublic();

        System.out.println(pub.getQ().getAffineXCoord().getEncoded().length);

        final String x = Hex.toHexString(pub.getQ().getAffineXCoord().getEncoded());
        final byte[] y = pub.getQ().getAffineYCoord().getEncoded();

        return ((y[y.length - 1] % 2 == 0) ? "03" : "02") + x;
    }

    public String getAddressAsString() {
        assert keyPair != null : "KeyPair is null, this should never happen";

        final BCECPublicKey pub = (BCECPublicKey)keyPair.getPublic();

        final byte[] xEnc = pub.getQ().getAffineXCoord().getEncoded();
        final byte[] yEnc = pub.getQ().getAffineYCoord().getEncoded();
        final byte[] s = new byte[xEnc.length + 3];
        s[0] = 0x21;
        s[s.length - 1] = (byte) 0xAC;
        s[1] = (byte) ((yEnc[yEnc.length - 1] % 2 == 0) ? 0x3 : 0x2);
        System.arraycopy(xEnc, 0, s, 2, xEnc.length);

        final SHA256Digest sha256Digest = new SHA256Digest();
        sha256Digest.update(s, 0, s.length);
        final byte[] sha256 = new byte[sha256Digest.getDigestSize()];
        sha256Digest.doFinal(sha256, 0);

        final RIPEMD160Digest r160Digest = new RIPEMD160Digest();
        r160Digest.update(sha256, 0, sha256.length);
        final byte[] r160 = new byte[r160Digest.getDigestSize()];
        r160Digest.doFinal(r160, 0);

        final byte[] sh = new byte[r160.length + 1];
        sh[0] = 53;
        System.arraycopy(r160, 0, sh, 1, r160.length);

        final SHA256Digest sha256Digest2 = new SHA256Digest();
        sha256Digest.update(sh, 0, sh.length);
        final byte[] sha2562 = new byte[sha256Digest2.getDigestSize()];
        sha256Digest.doFinal(sha2562, 0);

        final SHA256Digest sha256Digest3 = new SHA256Digest();
        sha256Digest.update(sha2562, 0, sha2562.length);
        final byte[] x = new byte[sha256Digest3.getDigestSize()];
        sha256Digest.doFinal(x, 0);

        final byte[] enc = new byte[sh.length + 4];
        System.arraycopy(sh, 0, enc, 0, sh.length);
        System.arraycopy(x, 0, enc, sh.length, 4);

        return Base58.encode(enc);
    }

}
