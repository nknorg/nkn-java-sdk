package jsmith.nknsdk.network;

import jsmith.nknsdk.utils.ThrowingLambda;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 *
 */
public class ConnectionProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionProvider.class);

    private static final Object lock = new Object();

    private static String[] bootstrapNodes = {

            "mainnet-seed-0001.nkn.org:30003",
            "mainnet-seed-0002.nkn.org:30003",
            "mainnet-seed-0003.nkn.org:30003",
            "mainnet-seed-0004.nkn.org:30003",
            "mainnet-seed-0005.nkn.org:30003",
            "mainnet-seed-0006.nkn.org:30003",
            "mainnet-seed-0007.nkn.org:30003",
            "mainnet-seed-0008.nkn.org:30003",
            "mainnet-seed-0009.nkn.org:30003",
            "mainnet-seed-0010.nkn.org:30003",
            "mainnet-seed-0011.nkn.org:30003",
            "mainnet-seed-0012.nkn.org:30003",
            "mainnet-seed-0013.nkn.org:30003",
            "mainnet-seed-0014.nkn.org:30003",
            "mainnet-seed-0015.nkn.org:30003",
            "mainnet-seed-0016.nkn.org:30003",
            "mainnet-seed-0017.nkn.org:30003",
            "mainnet-seed-0018.nkn.org:30003",
            "mainnet-seed-0019.nkn.org:30003",
            "mainnet-seed-0020.nkn.org:30003",
            "mainnet-seed-0021.nkn.org:30003",
            "mainnet-seed-0022.nkn.org:30003",
            "mainnet-seed-0023.nkn.org:30003",
            "mainnet-seed-0024.nkn.org:30003",
            "mainnet-seed-0025.nkn.org:30003",
            "mainnet-seed-0026.nkn.org:30003",
            "mainnet-seed-0027.nkn.org:30003",
            "mainnet-seed-0028.nkn.org:30003",
            "mainnet-seed-0029.nkn.org:30003",
            "mainnet-seed-0030.nkn.org:30003",
            "mainnet-seed-0031.nkn.org:30003",
            "mainnet-seed-0032.nkn.org:30003",
            "mainnet-seed-0033.nkn.org:30003",
            "mainnet-seed-0034.nkn.org:30003",
            "mainnet-seed-0035.nkn.org:30003",
            "mainnet-seed-0036.nkn.org:30003",
            "mainnet-seed-0037.nkn.org:30003",
            "mainnet-seed-0038.nkn.org:30003",
            "mainnet-seed-0039.nkn.org:30003",
            "mainnet-seed-0040.nkn.org:30003",
            "mainnet-seed-0041.nkn.org:30003",
            "mainnet-seed-0042.nkn.org:30003",
            "mainnet-seed-0043.nkn.org:30003",
            "mainnet-seed-0044.nkn.org:30003"

    };

    private static int maxRetries = 3;
    private static int rpcCallTimeoutMS = 5000;
    private static int messageAckTimeoutMS = 5000;
    private static int sessionTimeoutMS = 10000;


    public static int maxRetries() {
        synchronized (lock) {
            return maxRetries;
        }
    }
    public static void maxRetries(int maxRetries) {
        if (maxRetries < 0) throw new IllegalArgumentException("Max retries must be non-negative number");
        synchronized (lock) {
            ConnectionProvider.maxRetries = maxRetries;
        }
    }

    public static int rpcCallTimeoutMS() {
        synchronized (lock) {
            return rpcCallTimeoutMS;
        }
    }
    public static void rpcCallTimeoutMS(int rpcCallTimeoutMS) {
        if (rpcCallTimeoutMS < 0) throw new IllegalArgumentException("Timeout must be non-negative number");
        synchronized (lock) {
            ConnectionProvider.rpcCallTimeoutMS = rpcCallTimeoutMS;
        }
    }

    public static int messageAckTimeoutMS() {
        synchronized (lock) {
            return messageAckTimeoutMS;
        }
    }
    public static void messageAckTimeoutMS(int messageAckTimeoutMS) {
        if (messageAckTimeoutMS < 0) throw new IllegalArgumentException("Timeout must be non-negative number");
        synchronized (lock) {
            ConnectionProvider.messageAckTimeoutMS = messageAckTimeoutMS;
        }
    }

    public static int sessionTimeoutMS() {
        synchronized (lock) {
            return messageAckTimeoutMS;
        }
    }
    public static void sessionTimeoutMS(int sessionTimeoutMS) {
        if (sessionTimeoutMS < 0) throw new IllegalArgumentException("Timeout must be non-negative number");
        synchronized (lock) {
            ConnectionProvider.sessionTimeoutMS = sessionTimeoutMS;
        }
    }

    public static void setBootstrapNodes(String[] nodes) {
        synchronized (lock) {
            bootstrapNodes = nodes;
        }
    }

    public static <T> T attempt(ThrowingLambda<InetSocketAddress, T> action) throws Exception {
        final int retries = maxRetries();
        Exception error = null;

        final String[] nodes;
        synchronized (lock) {
            nodes = bootstrapNodes;
        }

        int nextNodeI = (int) (Math.random() * nodes.length);

        for (int i = 0; i <= retries; i++) {
            try {
                String hostname; int port;
                try {
                    final String addr = nodes[nextNodeI];
                    final int idx = addr.lastIndexOf(':');
                    hostname = addr.substring(0, idx);
                    port = Integer.parseInt(addr.substring(idx + 1));
                } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                    LOG.warn("Attempt {} failed", i);
                    LOG.warn("Invalid host address");
                    nextNodeI ++;
                    if (nextNodeI >= nodes.length) nextNodeI -= nodes.length;
                    continue;
                }
                return action.apply(new InetSocketAddress(hostname, port));
            } catch (Exception t) {
                error = t;
                LOG.warn("Attempt {} failed", i);
                LOG.debug("Caused by:", t);
            }
            Thread.sleep(rpcCallTimeoutMS);
            nextNodeI ++;
            if (nextNodeI >= nodes.length) nextNodeI -= nodes.length;
        }
        assert error != null;
        throw error;
    }

}
