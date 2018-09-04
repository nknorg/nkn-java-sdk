package jsmith.nknclient.wallet;

import com.darkyen.dave.WebbException;
import jsmith.nknclient.Const;
import jsmith.nknclient.client.NKNExplorer;
import jsmith.nknclient.utils.Base58;
import jsmith.nknclient.utils.Crypto;
import jsmith.nknclient.utils.HttpApi;
import jsmith.nknclient.utils.PasswordString;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import static jsmith.nknclient.utils.Crypto.aesEncryptAligned;
import static jsmith.nknclient.utils.Crypto.sha256;

/**
 *
 */
public class Wallet {

    private static final Logger LOG = LoggerFactory.getLogger(Wallet.class);
    private Wallet() {}
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    private static final SecureRandom secureRandom = new SecureRandom(); // TODO: Does not look thread safe (using it, not creating)

    private KeyPair keyPair = null;
    private String contractDataStr = "";

    public static Wallet createNew() {
        final Wallet w = new Wallet();

        try {
            ECGenParameterSpec ecGenSpec = new ECGenParameterSpec("secp256r1");
            KeyPairGenerator g = KeyPairGenerator.getInstance("ECDSA", "BC");
            g.initialize(ecGenSpec, secureRandom);

            w.keyPair = g.generateKeyPair();

        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            LOG.error("Couldn't generate wallet", e);
            throw new WalletError("Could not generate wallet", e);
        }

        return w;
    }

    public static Wallet createFromPrivateKey(byte[] privateKey) {
        try {

            final Wallet w = new Wallet();

            final ECNamedCurveParameterSpec ecNamedSpec = ECNamedCurveTable.getParameterSpec("secp256r1");
            final ECParameterSpec ecSpec = new ECNamedCurveSpec("secp256r1", ecNamedSpec.getCurve(), ecNamedSpec.getG(), ecNamedSpec.getN());
            final org.bouncycastle.jce.spec.ECParameterSpec ecbcSpec = new org.bouncycastle.jce.spec.ECParameterSpec(ecNamedSpec.getCurve(), ecNamedSpec.getG(), ecNamedSpec.getN());
            final KeyFactory kf = KeyFactory.getInstance("ECDSA", "BC");

            // TODO: investigate // negative private key for compatibility reasons, weird hack
            final ECPrivateKeySpec ecPrivKeySpec = new ECPrivateKeySpec(new BigInteger(-1, privateKey), ecSpec);
            final BCECPrivateKey privKey = (BCECPrivateKey) kf.generatePrivate(ecPrivKeySpec);
            final ECPublicKeySpec ecPubKeySpec = new ECPublicKeySpec(ecNamedSpec.getG().multiply(privKey.getD()), ecbcSpec);

            w.keyPair = new KeyPair(
                    kf.generatePublic(ecPubKeySpec),
                    privKey
            );

            return w;

        } catch (InvalidKeySpecException | NoSuchProviderException | NoSuchAlgorithmException e) {
            throw new WalletError("Creating wallet from private key failed", e);
        }
    }

    private static final String VERSION = "0.0.1";
    public static Wallet load(InputStream is, PasswordString password) {
        return load(is, -1, password);
    }
    public static Wallet load(InputStream is, int streamByteLimit, PasswordString password) {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read, limit = streamByteLimit == -1 ? buffer.length : streamByteLimit;
            while ((read = is.read(buffer, 0, Math.min(buffer.length, limit))) != -1) {
                baos.write(buffer, 0, read);
                if (streamByteLimit != -1) limit -= read;
            }
            baos.flush();
            final byte[] walletBytes = baos.toByteArray();


            JSONObject json = new JSONObject(new String(walletBytes, "UTF-8"));

            if (!json.getString("Version").equals(VERSION)) {
                throw new WalletError("Unsuported version of wallet save file: " + json.getString("Version"));
            }

            byte[] passwd = sha256(password.sha256());
            final byte[] passwordHash = Hex.decode(json.getString("PasswordHash"));
            if (!Arrays.equals(sha256(passwd), passwordHash)) {
                   LOG.warn("Unlocking wallet failed, wrong password");
                   return null;
            }

            final byte[] iv = Hex.decode(json.getString("IV"));
            final byte[] masterKeyEnc = Hex.decode(json.getString("MasterKey"));

            final byte[] masterKey = Crypto.aesDecryptAligned(masterKeyEnc, passwd, iv);
            final byte[] privateKey = Crypto.aesDecryptAligned(Hex.decode(json.getString("PrivateKeyEncrypted")), masterKey, iv);

            final Wallet w = createFromPrivateKey(privateKey);

            if (json.has("ContractData")) w.contractDataStr = json.getString("ContractData");

            // TODO check saved and generated address and program hash to see if address are not corrupted

            return w;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void save(OutputStream os, PasswordString password) {
        byte[] passwd = sha256(password.sha256());

        final JSONObject json = new JSONObject();

        json.put("Version", VERSION);
        json.put("Address", getAddressAsString());
        json.put("ProgramHash", getProgramHashAsHexString());
        json.put("PasswordHash", Hex.toHexString(sha256(passwd)));


        final byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        final byte[] masterKey = new byte[32];
        secureRandom.nextBytes(masterKey);

        json.put("IV", Hex.toHexString(iv));
        json.put("MasterKey", Hex.toHexString(aesEncryptAligned(masterKey, passwd, iv)));

        // negative private key for compatibility with compatibility mode (see comments in createFromPrivateKey function)
        final BCECPrivateKey privateKey = (BCECPrivateKey) keyPair.getPrivate();
        final byte[] dArr = privateKey.getParameters().getN().subtract(privateKey.getD()).toByteArray();

        final byte[] dArrTrimmed = new byte[32];
        System.arraycopy(dArr, dArr.length == 32 ? 0 : 1, dArrTrimmed, 0, dArrTrimmed.length);

        json.put("PrivateKeyEncrypted",
                Hex.toHexString(
                        aesEncryptAligned(dArrTrimmed, masterKey, iv)
                )
        );

        json.put("ContractData", contractDataStr);

        try {
            os.write(json.toString().getBytes("UTF-8"));
            os.flush();
        } catch (IOException ioe) {
            throw new WalletError("Wallet saving failed", ioe);
        }

    }

    public BigInteger queryBalance() {
        return queryBalance(Const.BOOTSTRAP_NODES_RPC);
    }
    public BigInteger queryBalance(InetSocketAddress bootstrapNodesRPC[]) {
        return NKNExplorer.queryBalance(bootstrapNodesRPC, getAddressAsString());
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


        final byte[] r160 = Crypto.r160(Crypto.sha256(s));

        final byte[] sh = new byte[r160.length + 1];
        sh[0] = 53;
        System.arraycopy(r160, 0, sh, 1, r160.length);

        final byte[] x = Crypto.doubleSha256(sh);

        final byte[] enc = new byte[sh.length + 4];
        System.arraycopy(sh, 0, enc, 0, sh.length);
        System.arraycopy(x, 0, enc, sh.length, 4);

        return Base58.encode(enc);
    }

    public String getProgramHashAsHexString() {
        final String addressStr = getAddressAsString();
        final byte[] address = Base58.decode(addressStr);
        final byte[] programHash = new byte[address.length - 5];
        System.arraycopy(address, 1, programHash, 0, programHash.length);
        return Hex.toHexString(programHash);
    }

}
