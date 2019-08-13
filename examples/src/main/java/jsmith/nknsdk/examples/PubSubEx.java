package jsmith.nknsdk.examples;

import com.darkyen.tproll.TPLogger;
import jsmith.nknsdk.client.*;
import jsmith.nknsdk.network.HttpApi;
import jsmith.nknsdk.wallet.Wallet;
import jsmith.nknsdk.wallet.WalletException;
import jsmith.nknsdk.wallet.WalletUtils;
import org.bouncycastle.util.encoders.Hex;

import java.io.File;
import java.math.BigDecimal;

import static jsmith.nknsdk.examples.LogUtils.setupLogging;

/**
 *
 */
public class PubSubEx {

    public static void main(String[] args) throws WalletException, InterruptedException, NKNClientException, NKNExplorerException {
        setupLogging(TPLogger.DEBUG);

        final File walletFile = new File("pubsub.dat");

        if (!walletFile.exists()) Wallet.createNew().save(walletFile, "pwd");

        final Wallet pubsubWallet = Wallet.load(walletFile, "pwd");

        System.out.println("Balance at " + pubsubWallet.getAddress() + " is " + pubsubWallet.queryBalance() + " tNKN");


        final String topic = "testtopic";

        final NKNExplorer.Subscription.Subscriber[] subscribers = NKNExplorer.Subscription.getSubscribers(topic, 0);
        System.out.println("Subscribers of '" + topic + "':");
        for (NKNExplorer.Subscription.Subscriber s : subscribers) {
            System.out.println("  " + s.fullClientIdentifier + (s.meta.isEmpty() ? "" : ": " + s.meta));
        }
        System.out.println("Total: " + subscribers.length + " subs");

        if (true) {

            final String identifier = "clientA";

            System.out.println("Subscribing to '" + topic + "' using " + identifier + (identifier == null || identifier.isEmpty() ? "" : ".") + Hex.toHexString(pubsubWallet.getPublicKey()));
            final String txID = pubsubWallet.tx().subscribe(topic, 0, 50, identifier, (String) null);

            if (txID == null) {
                System.out.println("  Transaction failed");
            } else {
                System.out.println("  Transaction successful: " + txID);
            }


            new NKNClient(new Identity(identifier, pubsubWallet)).onNewMessage(msg -> {
                if (msg.isText) {
                    System.out.println("New text from " + msg.from + "\n  ==> " + msg.textData);
                } else if (msg.isBinary) {
                    System.out.println("New binary from " + msg.from + "\n  ==> 0x" + Hex.toHexString(msg.binaryData.toByteArray()).toUpperCase());
                }
            }).start();
            Thread.sleep(1000);
        }

        new NKNClient(new Identity(null, Wallet.createNew())).start().publishTextMessageAsync(topic, 0, "Hello all my subscribers!");
        Thread.sleep(7000);


    }

}
