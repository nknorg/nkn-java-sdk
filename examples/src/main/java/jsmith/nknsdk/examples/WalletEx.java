package jsmith.nknsdk.examples;

import com.darkyen.tproll.TPLogger;
import jsmith.nknsdk.client.Identity;
import jsmith.nknsdk.client.NKNClient;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.client.NKNExplorer;
import jsmith.nknsdk.wallet.NKNTransactions;
import jsmith.nknsdk.wallet.Wallet;
import jsmith.nknsdk.wallet.WalletException;
import org.bouncycastle.util.encoders.Hex;

import java.io.File;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static jsmith.nknsdk.examples.LogUtils.setupLogging;

/**
 *
 */
public class WalletEx {

    public static void main(String[] args) throws WalletException {
        setupLogging(TPLogger.DEBUG);

//        final Wallet w = Wallet.createNew();
//        w.save(new File("devnet-tmpWallet.dat"), "a");
//        System.out.println("Generated: " + w.getAddress());

        final Wallet w2 = Wallet.load(new File("devnet-tmpWallet.dat"), "pwd");
        if (w2 != null) {
            System.out.println("Loaded: " + w2.getAddress());
        } else {
            System.out.println("Failed to decrypt saved wallet");
        }


        final String address = "NKNTknBaGnWXK7UB1KgnhaXrpJJc4qbCvwqs"; // Change/add/remove any char and see if it is still valid
        System.out.println("Address " + address + " is " + (NKNExplorer.isAddressValid(address) ? "" : "not ") + "valid");



        System.out.println();
        final File fromFile = new File("devnet-from.dat");
        final File toFile = new File("devnet-to.dat");

        if (!fromFile.exists()) Wallet.createNew().save(fromFile, "pwd");
        if (!toFile.exists()) Wallet.createNew().save(toFile, "pwd");

        final Wallet from = Wallet.load(fromFile, "pwd");
        final Wallet to = Wallet.load(toFile, "pwd");

        final String amount = "0.01";
        System.out.println("Transferring " + amount + " tNKN from " + from.getAddress() + " (" + from.queryBalance() + " tNKN) to " + to.getAddress() + " (" + to.queryBalance() + " tNKN)");

        if (false) {
            final String txID = NKNTransactions.transferTo(from, to.getAddress(), new BigDecimal(amount)); // Simple single transaction

            if (txID == null) {
                System.out.println("Transaction failed");
            } else {
                System.out.println("Transaction successful: " + txID);
            }
        } else {
            System.out.println("Transaction canceled");
        }


    }

}
