package jsmith.nknclient.network;

import java.net.InetSocketAddress;

/**
 *
 */
public class ConnectionProvider {

    private static final Object lock = new Object();

    private static InetSocketAddress[] bootstrapNodes = {
//            new InetSocketAddress("cluster2-oregon.nkn.org", 30003),
//            new InetSocketAddress("node00002.nkn.org", 30003),
//            new InetSocketAddress("104.196.227.157", 30003),

//            new InetSocketAddress("localhost", 30003),
            new InetSocketAddress("testnet-node-0001.nkn.org", 30003),
    };

    private static int maxRetries = 3;
    private static int rpcCallTimeoutMS = 5000;
    private static int messageAckTimeoutMS = 5000;


    public static int maxRetries() {
        synchronized (lock) {
            return maxRetries;
        }
    }
    public static void maxRetries(int maxRetries) {
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
        synchronized (lock) {
            ConnectionProvider.messageAckTimeoutMS = messageAckTimeoutMS;
        }
    }

    public static void setBootstrapNodes(InetSocketAddress[] nodes) {
        synchronized (lock) {
            bootstrapNodes = nodes;
        }
    }

    public static InetSocketAddress nextNode(int retries) {
        synchronized (lock) {
            if (retries > maxRetries) return null;
            return bootstrapNodes[(int) (Math.random() * bootstrapNodes.length)];
        }
    }

}
