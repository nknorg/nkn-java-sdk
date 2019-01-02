package jsmith.nknclient.wallet;

import jsmith.nknclient.Const;
import jsmith.nknclient.client.NKNExplorer;
import jsmith.nknclient.network.HttpApi;
import jsmith.nknclient.utils.Base58;
import jsmith.nknclient.utils.Crypto;
import jsmith.nknclient.utils.PasswordString;
import jsmith.nknclient.wallet.transactions.TransactionUtils;
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

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
    private static final SecureRandom secureRandom = new SecureRandom();

    private KeyPair keyPair = null;
    private String contractDataStr = "";

    public static Wallet createNew() {
        final Wallet w = new Wallet();

        try {
            ECGenParameterSpec ecGenSpec = new ECGenParameterSpec("secp256r1");
            KeyPairGenerator g = KeyPairGenerator.getInstance("ECDSA", "BC");
            g.initialize(ecGenSpec, secureRandom);

            w.keyPair = g.generateKeyPair();

            w.contractDataStr = Hex.toHexString(w.getSignatureData()) + "00" + w.getProgramHashAsHexString();

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

            final ECPrivateKeySpec ecPrivKeySpec = new ECPrivateKeySpec(new BigInteger(privateKey), ecSpec);
            final BCECPrivateKey privKey = (BCECPrivateKey) kf.generatePrivate(ecPrivKeySpec);
            final ECPublicKeySpec ecPubKeySpec = new ECPublicKeySpec(ecNamedSpec.getG().multiply(privKey.getD()), ecbcSpec);

            w.keyPair = new KeyPair(
                    kf.generatePublic(ecPubKeySpec),
                    privKey
            );

            w.contractDataStr = Hex.toHexString(w.getSignatureData()) + "00" + w.getProgramHashAsHexString();

            return w;

        } catch (InvalidKeySpecException | NoSuchProviderException | NoSuchAlgorithmException e) {
            throw new WalletError("Creating wallet from private key failed", e);
        }
    }

    private static final String VERSION = "0.0.1";
    public static Wallet load(File f, PasswordString password) {
        try {
            return load(new FileInputStream(f), -1, password);
        } catch (FileNotFoundException e) {
            throw new WalletError("Wallet loading failed - could not open file", e);
        }
    }
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

            JSONObject json = new JSONObject(new String(walletBytes, StandardCharsets.UTF_8));

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

            if (!json.has("ProgramHash") || !json.getString("ProgramHash").equals(w.getProgramHashAsHexString())) {
                throw new WalletError("Key mismatch in wallet file. Generated ProgramHash does not match the loaded ProgramHash");
            }

            if (!json.has("Address") || !json.getString("Address").equals(w.getAddressAsString())) {
                throw new WalletError("Key mismatch in wallet file. Generated Address does not match the loaded Address");
            }


            return w;
        } catch (IOException e) {
            throw new WalletError("Wallet loading failed - could not open file", e);
        }
    }

    public String transferTo(String toAddress, BigDecimal amount) {
        return transferTo(null, toAddress, amount);
    }
    public String transferTo(String txDescription, String toAddress, BigDecimal amount) {
        return transferTo(txDescription, new AssetTransfer(toAddress, amount));
    }
    public String transferTo(String txDescription, AssetTransfer ... transfers) {
        return transferTo(txDescription, Asset.T_NKN, transfers);
    }
    public String transferTo(String txDescription, Asset asset, AssetTransfer ... transfers) {
        final String inputsAndOutputsStr = TransactionUtils.genTxInputsAndOutputs(
                asset,
                HttpApi.getListUTXO(Const.BOOTSTRAP_NODES_RPC[0], getAddressAsString(), asset),
                getProgramHashAsHexString(),
                transfers
        );

        final String baseTransfer = TransactionUtils.rawBaseTransfer(txDescription, inputsAndOutputsStr);
        final String signature = WalletUtils.transferSignature(baseTransfer, keyPair.getPrivate());

        final String signatureRedeem = "23" + "21" + getPublicKeyAsHexString() + "ac";
        final String rawTxString = baseTransfer + signature + signatureRedeem;

        final JSONObject params = new JSONObject();
        params.put("tx", rawTxString);

        final JSONObject result = HttpApi.rpcCallJson(Const.BOOTSTRAP_NODES_RPC[0], "sendrawtransaction", params);
        if (result.has("result")) return result.getString("result");

        System.out.println("Error: " + result.toString());

        return null;
    }

    public void save(File file, PasswordString password) {
        try {
            save(new FileOutputStream(file), password);
        } catch (FileNotFoundException e) {
            throw new WalletError("Wallet saving failed", e);
        }
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

        final BCECPrivateKey privateKey = (BCECPrivateKey) keyPair.getPrivate();
        final byte[] dArr = privateKey.getD().toByteArray();

        final byte[] dArrTrimmed = new byte[32];
        System.arraycopy(dArr, dArr.length == 32 ? 0 : 1, dArrTrimmed, 0, dArrTrimmed.length);

        json.put("PrivateKeyEncrypted",
                Hex.toHexString(
                        aesEncryptAligned(dArrTrimmed, masterKey, iv)
                )
        );

        json.put("ContractData", contractDataStr);

        try {
            os.write(json.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException ioe) {
            throw new WalletError("Wallet saving failed", ioe);
        }

    }

    public BigDecimal queryBalance() {
        return queryBalance(Const.BOOTSTRAP_NODES_RPC, null);
    }
    public BigDecimal queryBalance(Asset asset) {
        return queryBalance(Const.BOOTSTRAP_NODES_RPC, asset);
    }
    public BigDecimal queryBalance(InetSocketAddress[] bootstrapNodesRPC) {
        return queryBalance(bootstrapNodesRPC, null);
    }
    public BigDecimal queryBalance(InetSocketAddress[] bootstrapNodesRPC, Asset asset) {
        return NKNExplorer.queryBalance(bootstrapNodesRPC, asset, getAddressAsString());
    }

    public String getPublicKeyAsHexString() {
        assert keyPair != null : "KeyPair is null, this should never happen";

        final BCECPublicKey pub = (BCECPublicKey)keyPair.getPublic();

        final String x = Hex.toHexString(pub.getQ().getAffineXCoord().getEncoded());
        final byte[] y = pub.getQ().getAffineYCoord().getEncoded();

        return ((y[y.length - 1] % 2 == 0) ? "02" : "03") + x;
    }

    public String getAddressAsString() {
        final byte[] s = getSignatureData();

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
        return WalletUtils.getProgramHashAsHexString(getAddressAsString());
    }

    public String getContractDataAsString() {
        return contractDataStr;
    }

    private byte[] getSignatureData() {
        assert keyPair != null : "KeyPair is null, this should never happen";

        final BCECPublicKey pub = (BCECPublicKey)keyPair.getPublic();

        final byte[] xEnc = pub.getQ().getAffineXCoord().getEncoded();
        final byte[] yEnc = pub.getQ().getAffineYCoord().getEncoded();
        final byte[] s = new byte[xEnc.length + 3];
        s[0] = 0x21;
        s[s.length - 1] = (byte) 0xAC;
        s[1] = (byte) ((yEnc[yEnc.length - 1] % 2 == 0) ? 0x2 : 0x3);
        System.arraycopy(xEnc, 0, s, 2, xEnc.length);

        return s;
    }

}
