package jsmith.nknsdk.examples;

import com.darkyen.tproll.TPLogger;
import jsmith.nknsdk.client.Identity;
import jsmith.nknsdk.client.NKNClient;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.wallet.Wallet;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 *
 */
public class SimpleEx {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleEx.class);

    public static void main(String[] args) throws InterruptedException {
        LogUtils.setupLogging(TPLogger.DEBUG);

        final Identity identityA = new Identity("Client A", Wallet.createNew());
        final Identity identityB = new Identity("Client B", Wallet.createNew());

        final NKNClient clientA = new NKNClient(identityA);
        try {
            clientA
                    .onNewMessage(receivedMessage -> {
                        if (receivedMessage.isText) {
                            System.out.println("Client A: New text from " + receivedMessage.from + "\n  ==> " + receivedMessage.textData);
                        } else if (receivedMessage.isBinary) {
                            System.out.println("Client A: New binary from " + receivedMessage.from + "\n  ==> 0x" + Hex.toHexString(receivedMessage.binaryData.toByteArray()).toUpperCase());
                        }
                    })
                    .start();
        } catch (NKNClientException e) {
            LOG.error("Client failed to start:", e);
            return;
        }

        final NKNClient clientB = new NKNClient(identityB);
        try {
            clientB
                    .onNewMessageWithReply(receivedMessage -> {
                        if (receivedMessage.isText) {
                            System.out.println("Client B: New text from " + receivedMessage.from + "\n  ==> " + receivedMessage.textData);
                        } else if (receivedMessage.isBinary) {
                            System.out.println("Client B: New binary from " + receivedMessage.from + "\n  ==> 0x" + Hex.toHexString(receivedMessage.binaryData.toByteArray()).toUpperCase());
                        }
                        return "Text message reply!";
                    })
                    .start();
        } catch (NKNClientException e) {
            LOG.error("Client failed to start: {}", e);
            return;
        }

        System.out.println("Started!");
        Thread.sleep(5000);

        final CompletableFuture<NKNClient.ReceivedMessage> promise = clientA.sendTextMessageAsync(identityB.getFullIdentifier(), "Hello!");
        promise.whenComplete((response, error) -> {
            if (error == null) {
                System.out.println("A: Response ==> " + response.textData);
                clientA.sendBinaryMessageAsync(identityB.getFullIdentifier(), null, new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}); // Casts because java (byte) is signed and these numbers would overwrite the msb
            } else {
                error.printStackTrace();
            }
        });


        Thread.sleep(7_000);
        System.out.println("Closing!");
        clientA.close();
        clientB.close();

    }

}
