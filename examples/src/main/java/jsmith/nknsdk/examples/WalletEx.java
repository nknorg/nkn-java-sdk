package jsmith.nknsdk.examples;

import com.darkyen.tproll.TPLogger;
import jsmith.nknsdk.client.Identity;
import jsmith.nknsdk.client.NKNClient;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.client.NKNExplorer;
import jsmith.nknsdk.wallet.Wallet;
import jsmith.nknsdk.wallet.WalletException;
import org.bouncycastle.util.encoders.Hex;

import java.io.File;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static jsmith.nknsdk.examples.LogUtils.setupLogging;

/**
 *
 */
public class WalletEx {

    public static void main(String[] args) throws WalletException {
        setupLogging(TPLogger.DEBUG);

//        final Wallet w = Wallet.createNew();
//        w.save(new File("devnet-tmpWallet.dat"), "a"); // PasswordString should be disposed by user after use
//        System.out.println("Generated: " + w.getAddressAsString());

        final Wallet w2 = Wallet.load(new File("devnet-tmpWallet.dat"), "pwd"); // PasswordString should be disposed by user after use
        if (w2 != null) {
            System.out.println("Loaded: " + w2.getAddressAsString());
        } else {
            System.out.println("Failed to decrypt saved wallet");
        }


        final String address = "NKNTknBaGnWXK7UB1KgnhaXrpJJc4qbCvwqs"; // Change/add/remove any char and see if it is still valid
        System.out.println("Address " + address + " is " + (NKNExplorer.isAddressValid(address) ? "" : "not ") + "valid");



        System.out.println();
        final File fromFile = new File("devnet-from.dat");
        final File toFile = new File("devnet-to.dat");

        if (!fromFile.exists()) Wallet.createNew().save(fromFile, "pwd");  // PasswordString should be disposed by user after use
        if (!toFile.exists()) Wallet.createNew().save(toFile, "pwd");

        final Wallet from = Wallet.load(fromFile, "pwd");
        final Wallet to = Wallet.load(toFile, "pwd");

        if (false) { // Request devNKN from faucet
            try {
                final NKNClient client = new NKNClient(new Identity("", from));
                client.onNewMessage(msg -> {
                    if (msg.isText) {
                        System.out.println("Received text from " + msg.from + "\n  ==> " + msg.textData);
                    } else if (msg.isBinary) {
                        System.out.println("Received binary from " + msg.from + "\n  ==> 0x" + Hex.toHexString(msg.binaryData.toByteArray()).toUpperCase());
                    } else if (msg.isAck) {
                        System.out.println("Received ack from " + msg.from);
                    } else {
                        System.out.println("Received unknown message from " + msg.from);
                    }
                });
                client.start();

                final CompletableFuture<NKNClient.ReceivedMessage> promise = client.sendTextMessageAsync("0149c42944eea91f094c16538eff0449d4d1e236f31c8c706b2e40e98402984c", "{" +
                        "\"address\": \"" + from.getAddressAsString() + "\"," +
                        "\"amount\": 10" +
                        "}");
                promise.whenComplete((msg, err) -> {
                    if (err == null) {
                        if (msg.isText) {
                            System.out.println("Reply text from " + msg.from + "\n  ==> " + msg.textData);
                        } else if (msg.isBinary) {
                            System.out.println("Reply binary from " + msg.from + "\n  ==> 0x" + Hex.toHexString(msg.binaryData.toByteArray()).toUpperCase());
                        } else if (msg.isAck) {
                            System.out.println("Reply ack from " + msg.from);
                        } else {
                            System.out.println("Reply unknown message from " + msg.from);
                        }
                    } else {
                        err.printStackTrace();
                    }
                });

                Thread.sleep(5000);
                client.close();

            } catch (NKNClientException e) {
                e.printStackTrace();
            } catch (InterruptedException ignored) {}
        }

        System.out.println("Transferring 0.01 tNKN from " + from.getAddressAsString() + " (" + from.queryBalance() + " tNKN) to " + to.getAddressAsString() + " (" + to.queryBalance() + " tNKN)");

        final String txID = from.transferTo(to.getAddressAsString(), new BigDecimal(0.01)); // Simple single transaction

        if (txID == null) {
            System.out.println("Transaction failed");
        } else {
            System.out.println("Transaction successful: " + txID);
        }


    }

}
