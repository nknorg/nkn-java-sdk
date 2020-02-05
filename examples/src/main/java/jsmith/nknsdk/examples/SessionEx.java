package jsmith.nknsdk.examples;

import com.darkyen.tproll.TPLogger;
import jsmith.nknsdk.client.Identity;
import jsmith.nknsdk.client.NKNClient;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.network.session.Session;
import jsmith.nknsdk.network.session.SessionOutputStream;
import jsmith.nknsdk.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

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
        clientB.sessionProtocol().setIncomingPreferredMulticlients(2);
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

                            byte[] buffer = new byte[1024 * 1024];
                            int redtotal = 0;
                            int red = bIs.read(buffer);
                            int lastMb = 0;
                            long thisTime, lastTime = System.currentTimeMillis();
                            while (red != -1) {
                                if (red > 0) {
//                                    String received = new String(buffer, 0, red, StandardCharsets.UTF_8);
//                                    System.out.println("Streamed to me: " + received);
                                    redtotal += red;
                                    if (redtotal / 1024 / 1024 > lastMb) {
                                        lastMb = redtotal / 1024 / 1024;
                                        thisTime = System.currentTimeMillis();
                                        // +1 to avoid division by zero, in case there is more than buffer.length data to be read
                                        System.out.println("Streamed to me: " + lastMb + " MB (" + (1024 * 1000 / (thisTime - lastTime + 1)) + "kB/s)");
                                        lastTime = thisTime;
                                    }
                                } else {
                                    Thread.sleep(500);
                                }
                                red = bIs.read(buffer);
                            }

                            LOG.info("All data has been received, session closed");

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
                    SessionOutputStream aOs = sA.getOutputStream();
                    try {
//                        aOs.write("Some random text here".getBytes(StandardCharsets.UTF_8));
//
                        int sendData = 1024 * 1024 * 10; // 10M
                        byte[] buffer = new byte[1024 * 1024];
                        while (sendData > 0) {
                            aOs.write(buffer);
                            sendData -= buffer.length;
                            System.out.println("Remaining to send " + sendData + " B");
                        }

                        aOs.flush();
                        LOG.debug("Flushing some data, session A -> session B");

                        // Wait until the other side acknowledges receiving everything TODO or timeout
                        while (aOs.getUnconfirmedSentBytesCount() > 0) {
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException ignored) {}
                        }

                        LOG.info("Everything was sent and read, closing");

                        sA.close();

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

        Thread.sleep(150_000);

        System.out.println("150s passed, closing clients!");
        clientA.close();
        clientB.close();

    }

}
