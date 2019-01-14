package jsmith.nknsdk.wallet;

import jsmith.nknsdk.client.NKNExplorer;
import jsmith.nknsdk.network.ConnectionProvider;
import jsmith.nknsdk.network.HttpApi;
import jsmith.nknsdk.utils.Base58;
import jsmith.nknsdk.wallet.transactions.TransactionUtils;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import static jsmith.nknsdk.utils.Crypto.*;

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
            throw new WalletException.WalletCryptoError("Could not generate wallet", e);
        }

        return w;
    }

    public static Wallet createFromPrivateKey(String hexPrivateKey) {
        return createFromPrivateKey(Hex.decode(hexPrivateKey));
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
            throw new WalletException.WalletCryptoError("Creating wallet from private key failed", e);
        }
    }

    private static final String VERSION = "0.0.1";
    public static Wallet load(File f, String password) throws WalletException {
        try {
            final FileInputStream fis = new FileInputStream(f);
            final Wallet w = load(fis, -1, password);
            fis.close();
            return w;
        } catch (FileNotFoundException e) {
            throw new WalletException("Wallet loading failed - could not open file", e);
        } catch (IOException e) {
            throw new WalletException("Wallet loading failed - IOException", e);
        }
    }
    public static Wallet load(InputStream is, String password) throws WalletException {
        return load(is, -1, password);
    }
    public static Wallet load(InputStream is, int streamByteLimit, String password) throws WalletException {
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
                throw new WalletException("Unsuported version of wallet save file: " + json.getString("Version"));
            }

            byte[] passwd = doubleSha256(password.getBytes(Charset.forName("UTF-8")));
            final byte[] passwordHash = Hex.decode(json.getString("PasswordHash"));
            if (!Arrays.equals(sha256(passwd), passwordHash)) {
                   LOG.warn("Unlocking wallet failed, wrong password");
                   return null;
            }

            final byte[] iv = Hex.decode(json.getString("IV"));
            final byte[] masterKeyEnc = Hex.decode(json.getString("MasterKey"));

            final byte[] masterKey = aesDecryptAligned(masterKeyEnc, passwd, iv);
            final byte[] privateKey = aesDecryptAligned(Hex.decode(json.getString("PrivateKeyEncrypted")), masterKey, iv);

            final Wallet w = createFromPrivateKey(privateKey);

            if (json.has("ContractData")) w.contractDataStr = json.getString("ContractData");

            if (!json.has("ProgramHash") || !json.getString("ProgramHash").equals(w.getProgramHashAsHexString())) {
                throw new WalletException("Key mismatch in wallet file. Generated ProgramHash does not match the loaded ProgramHash");
            }

            if (!json.has("Address") || !json.getString("Address").equals(w.getAddressAsString())) {
                throw new WalletException("Key mismatch in wallet file. Generated Address does not match the loaded Address");
            }


            return w;
        } catch (IOException e) {
            throw new WalletException("Wallet loading failed - could not read file", e);
        }
    }

    public String transferTo(String toAddress, BigDecimal amount) throws WalletException {
        return transferTo(null, toAddress, amount);
    }
    public String transferTo(String txDescription, String toAddress, BigDecimal amount) throws WalletException {
        return transferTo(txDescription, new AssetTransfer(toAddress, amount));
    }
    public String transferTo(String txDescription, AssetTransfer ... transfers) throws WalletException {
        return transferTo(txDescription, Asset.T_NKN, transfers);
    }
    public String transferTo(Asset asset, AssetTransfer ... transfers) throws WalletException {
        return transferTo(null, asset, transfers);
    }
    public String transferTo(String txDescription, Asset asset, AssetTransfer ... transfers) throws WalletException {
        JSONArray utxoList;

        try {
            utxoList = ConnectionProvider.attempt((node) -> {
                final JSONArray list = HttpApi.getListUTXO(node, getAddressAsString(), asset);
                if (list == null) throw new NullPointerException("Received 'null' instead of valid UTXO list");
                return list;
            });
        } catch (Throwable t) {
            if (t instanceof WalletException) throw (WalletException) t;
            throw new WalletException("Submitting a transaction failed", t);
        }


        final String inputsAndOutputsStr = TransactionUtils.genTxInputsAndOutputs(
                asset,
                utxoList,
                getProgramHashAsHexString(),
                transfers
        );

        final String baseTransfer = TransactionUtils.rawBaseTransfer(txDescription, inputsAndOutputsStr);
        final String signature = WalletUtils.transferSignature(baseTransfer, keyPair.getPrivate());

        final String signatureRedeem = "23" + "21" + getPublicKeyAsHexString() + "ac";
        final String rawTxString = baseTransfer + signature + signatureRedeem;

        final JSONObject params = new JSONObject();
        params.put("tx", rawTxString);

        try {
            return ConnectionProvider.attempt((node) -> {
                final JSONObject result = HttpApi.rpcCallJson(node, "sendrawtransaction", params);
                if (result.has("result") && result.getString("result") != null) {
                    return result.getString("result");
                } else {
                    throw new WalletException("Invalid response to query");
                }
            });
        } catch (Throwable t) {
            if (t instanceof WalletException) throw (WalletException) t;
            throw new WalletException("Submitting a transaction failed", t);
        }
    }

    public void save(File file, String password) throws WalletException {
        try {
            final FileOutputStream fos = new FileOutputStream(file);
            save(fos, password);
            fos.close();
        } catch (FileNotFoundException e) {
            throw new WalletException("Wallet saving failed. Cannot create missing file", e);
        } catch (IOException ioe) {
            throw new WalletException("Wallet saving failed, IOException", ioe);
        }
    }
    public void save(OutputStream os, String password) throws WalletException {
        byte[] passwd = doubleSha256(password.getBytes(Charset.forName("UTF-8")));

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
            throw new WalletException("Wallet saving failed, IOException", ioe);
        }

    }

    public BigDecimal queryBalance() throws WalletException {
        return NKNExplorer.queryBalance(getAddressAsString());
    }
    public BigDecimal queryBalance(Asset asset) throws WalletException {
        return NKNExplorer.queryBalance(asset, getAddressAsString());
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

        final byte[] r160 = r160(sha256(s));

        final byte[] sh = new byte[r160.length + 1];
        sh[0] = 53;
        System.arraycopy(r160, 0, sh, 1, r160.length);

        final byte[] x = doubleSha256(sh);

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
