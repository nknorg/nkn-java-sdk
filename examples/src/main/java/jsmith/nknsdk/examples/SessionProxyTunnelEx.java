package jsmith.nknsdk.examples;

import com.darkyen.tproll.TPLogger;
import jsmith.nknsdk.client.Identity;
import jsmith.nknsdk.client.NKNClient;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.network.session.Session;
import jsmith.nknsdk.wallet.Wallet;
import jsmith.nknsdk.wallet.WalletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 */
public class SessionProxyTunnelEx {

    private static final Logger LOG = LoggerFactory.getLogger(SessionProxyTunnelEx.class);

    public static void main(String[] args) throws InterruptedException {
        LogUtils.setupLogging(TPLogger.DEBUG);

        InetSocketAddress inetAddress;
        String nknAddress = "";

        boolean SERVER = false;

        if (SERVER) {
            inetAddress = new InetSocketAddress("127.0.0.1", 80);
            LOG.info("SERVER Mode. Target is {}", inetAddress);
        } else {
            inetAddress = new InetSocketAddress("127.0.0.1", 8080);
            nknAddress = "ProxyTunnel.2862c70b3113881d559575c280c995a5649519d95743e396c02b48a33c14ddeb";
            LOG.info("Client Mode. Target is :{} -> {}", inetAddress.getPort(), nknAddress);
        }



        File walletFile = new File("tmpWallet.dat");
        Wallet w;
        try {
            w = Wallet.load(walletFile, "pwd");
        } catch (WalletException e) {
            w = Wallet.createNew();
            try {
                w.save(walletFile, "pwd");
            } catch (WalletException ex) {
                LOG.error("Failed to save wallet");
                System.exit(1);
            }
        }

        final Identity identity = new Identity("ProxyTunnel" + (SERVER ? "" : "-client"), w);

        final NKNClient client = new NKNClient(identity);
        try {
            client.start();
        } catch (NKNClientException e) {
            LOG.error("Client failed to start:", e);
            return;
        }


        if (SERVER) {
            client.sessionProtocol().onSessionRequest(session -> {
                try {
                    setupSession(session, new Socket(inetAddress.getAddress(), inetAddress.getPort()));
                    return true;
                } catch (IOException e) {
                    LOG.error("Could not open proxy connection", e);
                    return false;
                }
            });
        } else {
            try {
                final ServerSocket ss = new ServerSocket(inetAddress.getPort());
                while (true) {
                    Socket s = ss.accept();
                    final Session session = client.sessionProtocol().dialSession(nknAddress);
                    setupSession(session, s);
                }
            } catch (NKNClientException | IOException e) {
                LOG.error("Dialing session failed", e);
            }

        }

        System.out.println("Started as " + identity.getFullIdentifier());
        Thread.sleep(100);


    }

    static void setupSession(Session session, Socket s) {
        session.onSessionEstablished(() -> {
            LOG.info("Established session");
            try {
                final InputStream inetIn = s.getInputStream();
                final OutputStream inetOut = s.getOutputStream();

                final InputStream nknIn = session.getInputStream();
                final OutputStream nknOut = session.getOutputStream();

                System.out.println("Got streams");

                new Thread(() -> {
                    byte[] buffer = new byte[1024 * 1024];
                    boolean closed = false;
                    try {
                        while (!closed) {
                            int red = inetIn.read(buffer);
                            System.out.println("Inet red " + red);
                            if (red != -1) {
                                if (red > 0) {
                                    nknOut.write(buffer, 0, red);
                                } else {
                                    Thread.sleep(50);
                                }
                            } else {
                                closed = true;
                            }
                        }
                    } catch (IOException | InterruptedException ioe) {
                        LOG.error("NetInNknOut IOe", ioe);
                    } finally {
                        try {
                            inetIn.close();
                        } catch (IOException ignored) {}
                        try {
                            nknOut.close();
                        } catch (IOException ignored) {}
                    }

                }, "ProxyTunnelExample-NetInNknOut").start();

                new Thread(() -> {
                    byte[] buffer = new byte[1024 * 1024];
                    boolean closed = false;
                    try {
                        while (!closed) {
                            int red = nknIn.read(buffer);
                            System.out.println("NKN red " + red);
                            if (red != -1) {
                                if (red > 0) {
                                    inetOut.write(buffer, 0, red);
                                } else {
                                    Thread.sleep(50);
                                }
                            } else {
                                closed = true;
                            }
                        }
                    } catch (IOException | InterruptedException ioe) {
                        LOG.error("NetOutNknIn IOe", ioe);
                    } finally {
                        try {
                            inetIn.close();
                        } catch (IOException ignored) {}
                        try {
                            nknOut.close();
                        } catch (IOException ignored) {}
                    }
                }, "ProxyTunnelExample-NetOutNknIn").start();

                System.out.println("Started threads");

            } catch (IOException e) {
                LOG.error("IOException", e);
            }
        });
    }

}
