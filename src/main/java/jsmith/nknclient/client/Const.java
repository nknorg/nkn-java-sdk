package jsmith.nknclient.client;

import java.net.InetSocketAddress;

/**
 *
 */
public class Const {

    public static final InetSocketAddress[] BOOTSTRAP_NODES_RPC = {
            new InetSocketAddress("cluster2-oregon.nkn.org", 30003),
    };

    public static final int RETRIES = 3;

}
