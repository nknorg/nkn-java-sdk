package jsmith.nknsdk.examples;

import com.darkyen.tproll.TPLogger;
import jsmith.nknsdk.client.Identity;
import jsmith.nknsdk.client.NKNClient;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.client.SimpleMessagesProtocol;
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
            clientA.simpleMessagesProtocol()
                    .onNewMessage(receivedMessage -> {
                        if (receivedMessage.isText) {
                            System.out.println("Client A: New " + (receivedMessage.wasEncrypted ? "encrypted" : "UNENCRYPTED") + " text from " + receivedMessage.from + "\n  ==> " + receivedMessage.textData);
                        } else if (receivedMessage.isBinary) {
                            System.out.println("Client A: New " + (receivedMessage.wasEncrypted ? "encrypted" : "UNENCRYPTED") + " binary from " + receivedMessage.from + "\n  ==> 0x" + Hex.toHexString(receivedMessage.binaryData.toByteArray()).toUpperCase());
                        }
                    });
            clientA.start();
        } catch (NKNClientException e) {
            LOG.error("Client failed to start:", e);
            return;
        }

        final NKNClient clientB = new NKNClient(identityB);
        try {
            clientB.simpleMessagesProtocol()
                    .onNewMessageWithReply(receivedMessage -> {
                        if (receivedMessage.isText) {
                            System.out.println("Client B: New " + (receivedMessage.wasEncrypted ? "encrypted" : "UNENCRYPTED") + " text from " + receivedMessage.from + "\n  ==> " + receivedMessage.textData);
                        } else if (receivedMessage.isBinary) {
                            System.out.println("Client B: New " + (receivedMessage.wasEncrypted ? "encrypted" : "UNENCRYPTED") + " binary from " + receivedMessage.from + "\n  ==> 0x" + Hex.toHexString(receivedMessage.binaryData.toByteArray()).toUpperCase());
                        }
                        return "Text message reply!";
                    });
            clientB
                    .setEncryptionLevel(NKNClient.EncryptionLevel.DO_NOT_ENCRYPT)
                    .start();
        } catch (NKNClientException e) {
            LOG.error("Client failed to start", e);
            return;
        }

        System.out.println("Started!");
        Thread.sleep(500);

        final CompletableFuture<SimpleMessagesProtocol.ReceivedMessage> promise = clientA.simpleMessagesProtocol().sendTextAsync(identityB.getFullIdentifier(), "Hello!");
        promise.whenComplete((response, error) -> {
            if (error == null) {
                System.out.println("A: " + (response.wasEncrypted ? "Encrypted" : "UNENCRYPTED") + " response ==> " + response.textData);
                clientA.simpleMessagesProtocol().sendBinaryAsync(identityB.getFullIdentifier(), null, new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}); // Casts because java (byte) is signed and these numbers would overwrite the msb
            } else {
                error.printStackTrace();
            }
        });

        for (int number = 0; number < 10; number ++) {
            Thread.sleep(5_000);
            clientA.simpleMessagesProtocol().sendTextAsync(identityB.getFullIdentifier(), "Message #" + number);
        }

        Thread.sleep(7_000);

        System.out.println("Closing!");
        clientA.close();
        clientB.close();

    }

}
