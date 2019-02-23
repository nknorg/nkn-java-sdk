package jsmith.nknsdk.examples;

import com.darkyen.tproll.LogFunction;
import com.darkyen.tproll.TPLogger;
import com.darkyen.tproll.logfunctions.FileLogFunction;
import com.darkyen.tproll.logfunctions.LogFunctionMultiplexer;
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

        final Wallet w = Wallet.createNew();
        w.save(new File("tmpWallet.dat"), "a"); // PasswordString should be disposed by user after use
        System.out.println("Generated: " + w.getAddressAsString());

        final Wallet w2 = Wallet.load(new File("tmpWallet.dat"), "a"); // PasswordString should be disposed by user after use
        if (w2 != null) {
            System.out.println("Loaded: " + w2.getAddressAsString());
        } else {
            System.out.println("Failed to decrypt saved wallet");
        }


        final String address = "NTyV6Yq1NbYf2ggwMRvHNkLHnrVFaeeQVD"; // Change/add/remove any char and see if it is still valid
        System.out.println("Address " + address + " is " + (NKNExplorer.isAddressValid(address) ? "" : "not ") + "valid");



        System.out.println();
        final File fromFile = new File("from.dat");
        final File toFile = new File("to.dat");

        if (!fromFile.exists()) Wallet.createNew().save(fromFile, "pwd");  // PasswordString should be disposed by user after use
        if (!toFile.exists()) Wallet.createNew().save(toFile, "pwd");

        final Wallet from = Wallet.load(fromFile, "pwd");
        final Wallet to = Wallet.load(toFile, "pwd");


        System.out.println("Transferring 1 tNKN from " + from.getAddressAsString() + " (" + from.queryBalance() + " tNKN) to " + to.getAddressAsString() + " (" + to.queryBalance() + " tNKN)");

        final String txID = from.transferTo("Hello world!", to.getAddressAsString(), new BigDecimal(1)); // Simple single transaction
//        final String txID = from.transferTo(
//                new AssetTransfer(to.getAddressAsString(), new BigDecimal(1)),
//                new AssetTransfer(to.getAddressAsString(), new BigDecimal(1))
//        ); // Multi transaction

        System.out.println("Transaction successful: " + txID);

    }

}
