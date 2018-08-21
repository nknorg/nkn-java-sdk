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
    public Identity(String name, Wallet w) {
        this.name = name;
        this.wallet = w;
    }

    public String getFullIdentifier() {
        if (name == null || name.isEmpty()) return wallet.getPublicKeyAsHexString();
        return name + "." + wallet.getPublicKeyAsHexString();
    }


    public String signStringAsString(String data) {

        LOG.warn("TODO: signStringAsString invoked, but not implemented yet."); // TODO

        return "";
    }

}
