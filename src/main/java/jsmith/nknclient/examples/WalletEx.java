package jsmith.nknclient.examples;

import com.darkyen.tproll.LogFunction;
import com.darkyen.tproll.TPLogger;
import com.darkyen.tproll.logfunctions.FileLogFunction;
import com.darkyen.tproll.logfunctions.LogFunctionMultiplexer;
import jsmith.nknclient.utils.PasswordString;
import jsmith.nknclient.wallet.Wallet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 *
 */
public class WalletEx {

    public static void main(String[] args) throws FileNotFoundException {
        TPLogger.DEBUG();
        TPLogger.setLogFunction(
                new LogFunctionMultiplexer(
                        LogFunction.DEFAULT_LOG_FUNCTION, // Log to console
                        new FileLogFunction(new File("logs")) // & Log to file in "logs" directory
                ));
        TPLogger.attachUnhandledExceptionLogger();

        final Wallet w = Wallet.createNew();
        w.save(new FileOutputStream(new File("tmpWallet.dat")), new PasswordString("a"));
        System.out.println("Generated: " + w.getAddressAsString());

        final Wallet w2 = Wallet.load(new FileInputStream(new File("tmpWallet.dat")), new PasswordString("a"));
        if (w2 != null) {
            System.out.println("Loaded: " + w2.getAddressAsString());
        } else {
            System.out.println("Failed to decrypt saved wallet");
        }

    }

}
