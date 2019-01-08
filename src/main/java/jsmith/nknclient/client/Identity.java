package jsmith.nknclient.client;

import jsmith.nknclient.wallet.Wallet;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;

/**
 *
 */
public class Identity {

    private static final Logger LOG = LoggerFactory.getLogger(Identity.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }


    public final String name;
    public final Wallet wallet;

    /**
     * Creates new identity for sending and receiving messages. Identity needs access to a wallet
     * @param name Name is used with combination with wallet to identify client. Can be null.
     * @param w wallet to use with this account. Cannot be null
     */
    public Identity(String name, Wallet w) {
        if (w == null) throw new NullPointerException("Wallet cannot be null");
        this.name = name;
        this.wallet = w;
    }

    public String getFullIdentifier() {
        if (name == null) return wallet.getPublicKeyAsHexString();
        return name + "." + wallet.getPublicKeyAsHexString();
    }

}
