package jsmith.nknsdk.examples;

import com.darkyen.tproll.TPLogger;
import jsmith.nknsdk.client.Identity;
import jsmith.nknsdk.client.NKNClient;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.client.SimpleMessages;
import jsmith.nknsdk.network.Session;
import jsmith.nknsdk.wallet.Wallet;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 *
 */
public class SessionEx {

    private static final Logger LOG = LoggerFactory.getLogger(SessionEx.class);

    public static void main(String[] args) throws InterruptedException {
        LogUtils.setupLogging(TPLogger.DEBUG);

        final Identity identityA = new Identity("Client A", Wallet.createNew());
        final Identity identityB = new Identity("Client B", Wallet.createNew());

        final NKNClient clientA = new NKNClient(identityA);
        try {
            clientA.start();
        } catch (NKNClientException e) {
            LOG.error("Client failed to start:", e);
            return;
        }

        final NKNClient clientB = new NKNClient(identityB);
        try {
             clientB.start();
        } catch (NKNClientException e) {
            LOG.error("Client failed to start", e);
            return;
        }

        System.out.println("Started!");
        Thread.sleep(500);

        try {
            clientB.sessionProtocol().onSessionRequest(sB -> {
                sB.onSessionEstablished(() -> {
                    Thread t = new Thread(() -> {
                        InputStream bIs = sB.getInputStream();
                        try {

                            byte[] buffer = new byte[100];
                            int red = bIs.read(buffer);
                            while (red != -1) {
                                if (red > 0) {
                                    String received = new String(buffer, 0, red, StandardCharsets.UTF_8);
                                    System.out.println("Streamed to me: " + received);
                                }
                                red = bIs.read(buffer);
                                Thread.sleep(500);
                            }
                        } catch (IOException | InterruptedException e) {
                            LOG.error("IOException thrown when sending data");
                        }
                    }, "SessionExample-InputStreamThread");
                    t.setDaemon(true);
                    t.start();
                });
                return true;
            });



            Session sA = clientA.sessionProtocol().dialSession(identityB.getFullIdentifier());
            LOG.info("Session dialed");

            sA.onSessionEstablished(() -> {
                Thread t = new Thread(() -> {
                    OutputStream aOs = sA.getOutputStream();
                    try {
                        aOs.write("Some random text here".getBytes(StandardCharsets.UTF_8));
                        aOs.flush();
                        LOG.debug("Flushing some data, session A -> session B");
                    } catch (IOException e) {
                        LOG.error("IOException thrown when sending data");
                    }

                }, "SessionExample-OutputStreamThread");
                t.setDaemon(true);
                t.start();
            });

        } catch (NKNClientException e) {
            LOG.error("Session dial failed");
        }

        Thread.sleep(130_000);

        System.out.println("Closing!");
        clientA.close();
        clientB.close();

    }

}
