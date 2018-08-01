package jsmith.nknclient.client;

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.*;
import java.security.spec.ECGenParameterSpec;

/**
 *
 */
public class Identity {

    private static final Logger LOG = LoggerFactory.getLogger(Identity.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private KeyPair keyPair;

    public final String name;
    public Identity(String name) { // TODO persistence (create from save file) and create from hex string or something
        this.name = name;

        keyPair = null;
        try {
            ECGenParameterSpec ecGenSpec = new ECGenParameterSpec("secp256r1");
            KeyPairGenerator g = KeyPairGenerator.getInstance("ECDSA", "BC");
            g.initialize(ecGenSpec, new SecureRandom());

            keyPair = g.generateKeyPair();

        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            LOG.error("Couldn't generate identity key", e);
        }
    }

    public String getFullIdentifier() {
        assert keyPair != null : "KeyPair is null, this should never happen";

        if (name == null || name.isEmpty()) return getPublicKeyAsString();
        return name + "." + getPublicKeyAsString();
    }

    public String getPublicKeyAsString() {
        assert keyPair != null : "KeyPair is null, this should never happen";

        final BCECPublicKey pub = (BCECPublicKey)keyPair.getPublic();

        final String x = Hex.toHexString(pub.getQ().getAffineXCoord().getEncoded());
        final String y = Hex.toHexString(pub.getQ().getAffineYCoord().getEncoded());

        return "04" + x + y; // TODO what does 04 mean and why is in required?
    }

    public String singStringAsString(String data) {
        assert keyPair != null : "KeyPair is null, this should never happen";

        LOG.warn("TODO: signStringAsString invoked, but not implemented yet."); // TODO

        return "";
    }

}
