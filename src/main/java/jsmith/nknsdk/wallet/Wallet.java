package jsmith.nknsdk.wallet;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.client.NKNExplorer;
import jsmith.nknsdk.network.ConnectionProvider;
import jsmith.nknsdk.network.HttpApi;
import jsmith.nknsdk.wallet.transactions.TransactionT;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Arrays;

import static jsmith.nknsdk.utils.Crypto.*;

/**
 *
 */
public class Wallet {

    private static final Logger LOG = LoggerFactory.getLogger(Wallet.class);
    private Wallet() {}
    private static final SecureRandom secureRandom = new SecureRandom();

    private KeyPair keyPair = null;
    private byte[] seed;
    private String contractDataStr = "";

    private final static EdDSAParameterSpec ED25519 = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);

    public static Wallet createNew() {
        byte[] seed = new byte[ED25519.getCurve().getField().getb() / 8];
        secureRandom.nextBytes(seed);

        return createFromSeed(seed);
    }

    public static Wallet createFromSeed(String seed) {
        return createFromSeed(Hex.decode(seed));
    }

    public static Wallet createFromSeed(byte[] seed) {
        final Wallet w = new Wallet();

        final EdDSAPrivateKeySpec privateSpec = new EdDSAPrivateKeySpec(seed, ED25519);
        final EdDSAPublicKeySpec publicSpec = new EdDSAPublicKeySpec(privateSpec.getA(), ED25519);

        w.keyPair = new KeyPair(
                new EdDSAPublicKey(publicSpec),
                new EdDSAPrivateKey(privateSpec)
        );

        w.seed = seed;

        w.contractDataStr = Hex.toHexString(WalletUtils.getSignatureRedeemFromPublicKey(w.getPublicKey())) + "00" + Hex.toHexString(w.getProgramHash().toByteArray());

        return w;
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
            final byte[] seed = aesDecryptAligned(Hex.decode(json.getString("SeedEncrypted")), masterKey, iv);

            final Wallet w = createFromSeed(seed);

            if (json.has("ContractData")) w.contractDataStr = json.getString("ContractData");

            if (!json.has("ProgramHash") || !json.getString("ProgramHash").equalsIgnoreCase(Hex.toHexString(w.getProgramHash().toByteArray()))) {
                throw new WalletException("Key mismatch in wallet file. Generated ProgramHash does not match the loaded ProgramHash");
            }

            if (!json.has("Address") || !json.getString("Address").equalsIgnoreCase(w.getAddress())) {
                throw new WalletException("Key mismatch in wallet file. Generated Address does not match the loaded Address");
            }


            return w;
        } catch (IOException e) {
            throw new WalletException("Wallet loading failed - could not read file", e);
        }
    }

    public String submitTransaction(TransactionT tx) throws WalletException {
        final String txRaw = Hex.toHexString(tx.build((EdDSAPrivateKey) keyPair.getPrivate(), ByteString.copyFrom(WalletUtils.getSignatureRedeemFromPublicKey(getPublicKey()))).toByteArray());
        try {
            return ConnectionProvider.attempt((bootstrapNode) -> HttpApi.sendRawTransaction(bootstrapNode, txRaw));
        } catch (Exception t) {
            if (t instanceof WalletException) throw (WalletException) t;
            throw new WalletException("Failed to send transaction", t);
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
        json.put("Address", getAddress());
        json.put("ProgramHash", Hex.toHexString(getProgramHash().toByteArray()));
        json.put("PasswordHash", Hex.toHexString(sha256(passwd)));


        final byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        final byte[] masterKey = new byte[32];
        secureRandom.nextBytes(masterKey);

        json.put("IV", Hex.toHexString(iv));
        json.put("MasterKey", Hex.toHexString(aesEncryptAligned(masterKey, passwd, iv)));

        json.put("SeedEncrypted",
                Hex.toHexString(
                        aesEncryptAligned(seed, masterKey, iv)
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
        return NKNExplorer.queryBalance(getAddress());
    }
    public BigDecimal queryBalance(Asset asset) throws WalletException {
        return NKNExplorer.queryBalance(asset, getAddress());
    }

    public byte[] getPublicKey() {
        assert keyPair != null : "KeyPair is null, this should never happen";

        final byte[] encodedWithPrefix = keyPair.getPublic().getEncoded();
        final byte[] encoded = new byte[32];
        System.arraycopy(encodedWithPrefix, encodedWithPrefix.length - encoded.length, encoded, 0, encoded.length);

        return encoded;
    }
    public String getAddress() {
        return WalletUtils.getAddressFromProgramHash(getProgramHash());
    }

    public ByteString getProgramHash() {
        return ByteString.copyFrom(WalletUtils.getProgramHashFromPublicKey(getPublicKey()));
    }

    public String getContractDataAsString() {
        return contractDataStr;
    }

    public NKNTransaction tx() {
        return new NKNTransaction(this);
    }

}
