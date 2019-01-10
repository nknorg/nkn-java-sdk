package jsmith.nknclient.examples;

import com.darkyen.tproll.LogFunction;
import com.darkyen.tproll.TPLogger;
import com.darkyen.tproll.logfunctions.FileLogFunction;
import com.darkyen.tproll.logfunctions.LogFunctionMultiplexer;
import jsmith.nknclient.client.Identity;
import jsmith.nknclient.client.NKNClient;
import jsmith.nknclient.wallet.Wallet;
import org.bouncycastle.util.encoders.Hex;

import java.io.File;
import java.util.concurrent.CompletableFuture;

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

        final Identity identityA = new Identity("Client A", Wallet.createNew());
        final Identity identityB = new Identity("Client B", Wallet.createNew());

        final NKNClient clientA = new NKNClient(identityA)
                .onNewMessage(receivedMessage -> {
                    if (receivedMessage.isText) {
                        System.out.println("Client A: New text from " + receivedMessage.from + "\n  ==> " + receivedMessage.textData);
                    } else if (receivedMessage.isBinary) {
                        System.out.println("Client A: New binary from " + receivedMessage.from + "\n  ==> 0x" + Hex.toHexString(receivedMessage.binaryData.toByteArray()).toUpperCase());
                    }
                })
                .start();
        final NKNClient clientB = new NKNClient(identityB)
                .onNewMessageWithReply(receivedMessage -> {
                    if (receivedMessage.isText) {
                        System.out.println("Client B: New text from " + receivedMessage.from + "\n  ==> " + receivedMessage.textData);
                    } else if (receivedMessage.isBinary) {
                        System.out.println("Client B: New binary from " + receivedMessage.from + "\n  ==> 0x" + Hex.toHexString(receivedMessage.binaryData.toByteArray()).toUpperCase());
                    }
                    return "Text message ACK!";
                })
                .start();

        final CompletableFuture<NKNClient.ReceivedMessage> promise = clientA.sendTextMessageAsync(identityB.getFullIdentifier(), null, "Hello!");
        promise.whenComplete((response, error) -> {
            if (error == null) {
                System.out.println("A: Response ==> " + response.textData);
                clientA.sendBinaryMessageAsync(identityB.getFullIdentifier(), null, new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}); // Casts because java (byte) is signed and these numbers would overwrite the msb
            } else {
                error.printStackTrace();
            }
        });


        Thread.sleep(7_000);
        clientA.close();
        clientB.close();

    }

}
