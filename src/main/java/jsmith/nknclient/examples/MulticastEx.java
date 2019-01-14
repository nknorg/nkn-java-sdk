package jsmith.nknclient.examples;

import com.darkyen.tproll.LogFunction;
import com.darkyen.tproll.TPLogger;
import com.darkyen.tproll.logfunctions.FileLogFunction;
import com.darkyen.tproll.logfunctions.LogFunctionMultiplexer;
import jsmith.nknclient.client.Identity;
import jsmith.nknclient.client.NKNClient;
import jsmith.nknclient.client.NKNClientException;
import jsmith.nknclient.wallet.Wallet;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 *
 */
public class MulticastEx {

    private static final Logger LOG = LoggerFactory.getLogger(MulticastEx.class);

    public static void main(String[] args) throws InterruptedException, NKNClientException {
        TPLogger.INFO();
        TPLogger.setLogFunction(
                new LogFunctionMultiplexer(
                        LogFunction.DEFAULT_LOG_FUNCTION, // Log to console
                        new FileLogFunction(new File("logs")) // & Log to file in "logs" directory
                ));
        TPLogger.attachUnhandledExceptionLogger();

        final Identity identitySender = new Identity("Sender", Wallet.createNew());
        final Identity identityA = new Identity("A", Wallet.createNew());
        final Identity identityB = new Identity("B", Wallet.createNew());
        final Identity identityC = new Identity("C", Wallet.createNew());

        LOG.info("Initializing clients");

        final NKNClient clientSender = new NKNClient(identitySender)
                .onNewMessage(receivedMessage -> {
                    if (receivedMessage.isText) {
                        System.out.println("Sender: New text from " + receivedMessage.from + "\n  ==> " + receivedMessage.textData);
                    } else if (receivedMessage.isBinary) {
                        System.out.println("Sender: New binary from " + receivedMessage.from + "\n  ==> 0x" + Hex.toHexString(receivedMessage.binaryData.toByteArray()).toUpperCase());
                    }
                })
                .start();

        // These will just reply with ACK
        final NKNClient clientA = new NKNClient(identityA).start();
        final NKNClient clientB = new NKNClient(identityB).start();
        final NKNClient clientC = new NKNClient(identityC).start();

        LOG.info("All clients ready, broadcasting");

        // Change one of the addresses or dont start one client to see what happens if the message is not sent and received correctly
        final List<CompletableFuture<NKNClient.ReceivedMessage>> promises = clientSender.sendTextMessageMulticastAsync(new String[] {
                identityA.getFullIdentifier(),
                identityB.getFullIdentifier(),
                identityC.getFullIdentifier()
        }, "Hello!");

        promises.forEach(p -> p.whenComplete((response, error) -> {
            if (error == null) {
                System.out.println("Response from " + response.from);
                System.out.println("  ==> " + (response.isAck ? "[ACK]" : response.isText ? response.textData : ("0x" + Hex.toHexString(response.binaryData.toByteArray()).toUpperCase())));
            } else {
                System.out.println("Error: " + error.toString());
            }
        }));


        Thread.sleep(7_000);

        LOG.info("Closing all clients");

        clientSender.close();
        clientA.close();
        clientB.close();
        clientC.close();

    }

}
