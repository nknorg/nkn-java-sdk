package jsmith.nknclient.examples;

import com.darkyen.tproll.LogFunction;
import com.darkyen.tproll.TPLogger;
import com.darkyen.tproll.logfunctions.FileLogFunction;
import com.darkyen.tproll.logfunctions.LogFunctionMultiplexer;
import jsmith.nknclient.client.NKNExplorer;
import jsmith.nknclient.utils.PasswordString;
import jsmith.nknclient.wallet.Wallet;

import java.io.File;

/**
 *
 */
public class WalletEx {

    public static void main(String[] args) {
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

    }

}
