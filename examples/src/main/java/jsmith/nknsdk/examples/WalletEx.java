package jsmith.nknsdk.examples;

import com.darkyen.tproll.TPLogger;
import jsmith.nknsdk.client.NKNExplorer;
import jsmith.nknsdk.wallet.Wallet;
import jsmith.nknsdk.wallet.WalletException;

import java.io.File;
import java.math.BigDecimal;

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

        System.out.println("Balance at " + from.getAddress() + " is " + from.queryBalance() + " tNKN");
        final String nsQueryName = "somename";
        System.out.println("Name '" + nsQueryName + "' is registered to: " + NKNExplorer.resolveNamedAddress(nsQueryName));

        if (false) {
            final String name = "somename";
            System.out.println("Registering '" + name + "' to " + from.getAddress());

            final String nameTxID = from.tx().registerName(name); // Simple registerName transaction

            if (nameTxID == null) {
                System.out.println("  Transaction failed");
            } else {
                System.out.println("  Transaction successful: " + nameTxID);
            }
        }

        if (true) {
            final String amount = "1";
            System.out.println("Transferring " + amount + " tNKN from " + from.getAddress() + " (" + from.queryBalance() + " tNKN) to " + to.getAddress() + " (" + to.queryBalance() + " tNKN)");


            final String txID = from.tx().transferTo(to.getAddress(), new BigDecimal(amount)); // Simple single transaction

            if (txID == null) {
                System.out.println("  Transaction failed");
            } else {
                System.out.println("  Transaction successful: " + txID);
            }
        }


    }

}
