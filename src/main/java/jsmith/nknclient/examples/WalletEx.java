package jsmith.nknclient.examples;

import com.darkyen.tproll.LogFunction;
import com.darkyen.tproll.TPLogger;
import com.darkyen.tproll.logfunctions.FileLogFunction;
import com.darkyen.tproll.logfunctions.LogFunctionMultiplexer;
import jsmith.nknclient.Const;
import jsmith.nknclient.client.NKNExplorer;
import jsmith.nknclient.network.HttpApi;
import jsmith.nknclient.utils.PasswordString;
import jsmith.nknclient.wallet.AssetTransfer;
import jsmith.nknclient.wallet.Wallet;

import java.io.File;
import java.math.BigDecimal;

/**
 *
 */
public class WalletEx {

    public static void main(String[] args) throws InterruptedException {
        TPLogger.DEBUG();
        TPLogger.setLogFunction(
                new LogFunctionMultiplexer(
                        LogFunction.DEFAULT_LOG_FUNCTION, // Log to console
                        new FileLogFunction(new File("logs")) // & Log to file in "logs" directory
                ));
        TPLogger.attachUnhandledExceptionLogger();

        final Wallet w = Wallet.createNew();
        w.save(new File("tmpWallet.dat"), new PasswordString("a")); // PasswordString should be disposed by user after use
        System.out.println("Generated: " + w.getAddressAsString());

        final Wallet w2 = Wallet.load(new File("tmpWallet.dat"), new PasswordString("a")); // PasswordString should be disposed by user after use
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

        if (!fromFile.exists()) Wallet.createNew().save(fromFile, new PasswordString("pwd"));  // PasswordString should be disposed by user after use
        if (!toFile.exists()) Wallet.createNew().save(toFile, new PasswordString("pwd"));

        final Wallet from = Wallet.load(fromFile, new PasswordString("pwd"));
        final Wallet to = Wallet.load(toFile, new PasswordString("pwd"));

        System.out.println("Target wallet balance: " + to.queryBalance());

        System.out.println("Transferring 1 NKN from " + from.getAddressAsString() + " to " + to.getAddressAsString());

        // final String txID = from.transferTo(to.getAddressAsString(), new BigDecimal(1)); // Simple single transaction
        final String txID = from.transferTo(new AssetTransfer(to.getAddressAsString(), new BigDecimal(1)), new AssetTransfer(to.getAddressAsString(), new BigDecimal(1))); // Multi transaction

        if (txID != null) {
            System.out.println("Transaction successful: " + txID);
        } else {
            System.out.println("Transaction failed");
        }

        Thread.sleep(10000); // Wait for network to reflect changes
        System.out.println("After transfer, target wallet balance: " + to.queryBalance());

    }

}
