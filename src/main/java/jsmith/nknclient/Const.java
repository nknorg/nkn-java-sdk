package jsmith.nknclient;

import java.net.InetSocketAddress;

/**
 *
 */
public class Const {

    public static final InetSocketAddress[] BOOTSTRAP_NODES_RPC = {
//            new InetSocketAddress("cluster2-oregon.nkn.org", 30003),
//            new InetSocketAddress("node00002.nkn.org", 30003),
//            new InetSocketAddress("104.196.227.157", 30003),
            new InetSocketAddress("testnet-node-0001.nkn.org", 30003),
    };

    public static final int RETRIES = 3;

    public static final int RPC_CALL_TIMEOUT_MS = 5000;

    public static final int MESSAGE_ACK_TIMEOUT_MS = 5000;

    public static final int NKN_ACC_MUL = 100000000;

}
