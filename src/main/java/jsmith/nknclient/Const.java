package jsmith.nknclient;

import java.net.InetSocketAddress;

/**
 *
 */
public class Const {

    public static final InetSocketAddress[] BOOTSTRAP_NODES_RPC = {
//            new InetSocketAddress("cluster2-oregon.nkn.org", 30003),
            new InetSocketAddress("node00002.nkn.org", 30003),
//            new InetSocketAddress("104.196.227.157", 30003),
    };

    public static final int RETRIES = 3;

    public static final String BALANCE_ASSET_ID = "4945ca009174097e6614d306b66e1f9cb1fce586cb857729be9e1c5cc04c9c02";

}
