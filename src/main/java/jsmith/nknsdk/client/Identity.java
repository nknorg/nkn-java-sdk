package jsmith.nknsdk.client;

import jsmith.nknsdk.wallet.Wallet;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class Identity {

    private static final Logger LOG = LoggerFactory.getLogger(Identity.class);


    public final String name;
    public final Wallet wallet;

    /**
     * Creates new identity for sending and receiving messages. Identity needs access to a wallet
     * @param name Name is used with combination with wallet to identify client. Can be null.
     * @param w wallet to use with this account. Cannot be null
     */
    public Identity(String name, Wallet w) {
        if (w == null) throw new NullPointerException("Wallet cannot be null");
        this.name = name == null ? "" : name;
        this.wallet = w;
    }

    public String getFullIdentifier() {
        if (name.isEmpty()) return Hex.toHexString(wallet.getPublicKey());
        return name + "." + Hex.toHexString(wallet.getPublicKey());
    }

}
