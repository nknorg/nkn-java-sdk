package jsmith.nknsdk.examples;

import com.darkyen.tproll.TPLogger;
import jsmith.nknsdk.client.NKNExplorer;
import jsmith.nknsdk.network.HttpApi;
import jsmith.nknsdk.wallet.Wallet;
import jsmith.nknsdk.wallet.WalletException;

import java.io.File;
import java.math.BigDecimal;

import static jsmith.nknsdk.examples.LogUtils.setupLogging;

/**
 *
 */
public class PubSubEx {

    public static void main(String[] args) throws WalletException {
        setupLogging(TPLogger.DEBUG);

        final File walletFile = new File("pubsub.dat");

        if (!walletFile.exists()) Wallet.createNew().save(walletFile, "pwd");

        final Wallet pubsubWallet = Wallet.load(walletFile, "pwd");

        System.out.println("Balance at " + pubsubWallet.getAddress() + " is " + pubsubWallet.queryBalance() + " tNKN");


        final String topic = "testtopic5";

        final NKNExplorer.Subscriber[] subscribers = NKNExplorer.getSubscribers(topic, 0);
        System.out.println("Subscribers of '" + topic + "':");
        for (NKNExplorer.Subscriber s : subscribers) {
            System.out.println("  " + s.fullClientIdentifier + (s.meta.isEmpty() ? "" : ": " + s.meta));
        }
        System.out.println("Total: " + subscribers.length + " subs");

        if (true) {

            System.out.println("Subscribing to '" + topic + "' using " + pubsubWallet.getAddress());
            final String txID = pubsubWallet.tx().subscribe(topic, 0, 100, "somename", "meta");

            if (txID == null) {
                System.out.println("  Transaction failed");
            } else {
                System.out.println("  Transaction successful: " + txID);
            }
        }

    }

}
