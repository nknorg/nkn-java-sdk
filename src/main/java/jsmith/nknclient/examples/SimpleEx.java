package jsmith.nknclient.examples;

import com.darkyen.tproll.LogFunction;
import com.darkyen.tproll.TPLogger;
import com.darkyen.tproll.logfunctions.FileLogFunction;
import com.darkyen.tproll.logfunctions.LogFunctionMultiplexer;
import jsmith.nknclient.client.Identity;
import jsmith.nknclient.client.NKNClient;
import jsmith.nknclient.utils.Crypto;
import jsmith.nknclient.wallet.Wallet;
import org.bouncycastle.util.encoders.Hex;

import java.io.File;

/**
 *
 */
public class SimpleEx {

    public static void main(String[] args) throws InterruptedException {
        TPLogger.DEBUG();
        TPLogger.setLogFunction(
                new LogFunctionMultiplexer(
                        LogFunction.DEFAULT_LOG_FUNCTION, // Log to console
                        new FileLogFunction(new File("logs")) // & Log to file in "logs" directory
                ));
        TPLogger.attachUnhandledExceptionLogger();

        final Identity identityA = new Identity("Node.A", Wallet.createNew());
        final Identity identityB = new Identity("Node.B", Wallet.createNew());

        final NKNClient clientA = new NKNClient(identityA)
                .onTextMessage((from, message) -> System.out.println("ClientA: New message from " + from + "\n  ==> " + message))
                .onBinaryMessage((from, message) -> System.out.println("ClientA: New binary from " + from + "\n  ==> 0x" + Hex.toHexString(message.toByteArray())))
                .start();
        final NKNClient clientB = new NKNClient(identityB)
                .onTextMessage((from, message) -> System.out.println("ClientB: New message from " + from + "\n  ==> " + message))
                .onBinaryMessage((from, message) -> System.out.println("ClientB: New binary from " + from + "\n  ==> 0x" + Hex.toHexString(message.toByteArray())))
                .start();

        clientA.sendTextMessage(identityB.getFullIdentifier(), "Hello!");
        clientB.sendBinaryMessage(identityA.getFullIdentifier(), new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}); // Casts because java (byte) is signed and these numbers would overwrite the msb

        Thread.sleep(5000);
        clientA.close();
        clientB.close();

    }

}
