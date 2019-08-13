package jsmith.nknsdk.client;

import jsmith.nknsdk.network.ConnectionProvider;
import jsmith.nknsdk.network.HttpApi;
import jsmith.nknsdk.utils.Base58;
import jsmith.nknsdk.utils.Crypto;
import jsmith.nknsdk.wallet.WalletException;
import jsmith.nknsdk.wallet.WalletUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.HashMap;

/**
 *
 */
public class NKNExplorer {

    private static final Logger LOG = LoggerFactory.getLogger(NKNExplorer.class);

    private NKNExplorer() {} // Not instantiable

    public static boolean isAddressValid(String address) {
        try {

            final byte[] addressBytes = Base58.decode(address);
            if (addressBytes.length != WalletUtils.ADDRESS_PREFIX.length + 20 + 4) return false;

            for (int i = 0; i < WalletUtils.ADDRESS_PREFIX.length; i++) {
                if (addressBytes[i] != WalletUtils.ADDRESS_PREFIX[i]) return false;
            }

            final byte[] sh = new byte[addressBytes.length - 4];
            System.arraycopy(addressBytes, 0, sh, 0, sh.length);

            final byte[] check = Crypto.doubleSha256(sh);
            for (int i = 0; i < 4; i++) {
                if (check[i] != addressBytes[sh.length + i]) return false;
            }

            return true;

        } catch (IllegalArgumentException e) { // Not Base58 input
            return false;
        }
    }

    public static class Wallet {
        private Wallet() {} // Not instantiable

        public static String resolveNamedAddress(String name) throws NKNExplorerException {
            // https://github.com/nknorg/nkn/blob/master/api/common/interfaces.go#L1070
            try {
                final HashMap<String, String> params = new HashMap<>();
                params.put("name", name);
                return (String) ConnectionProvider.attempt((node) -> HttpApi.rpcRequest(node, "getaddressbyname", params));
            } catch (Exception t) {
                if (t instanceof NKNExplorerException) throw (NKNExplorerException) t;
                throw new NKNExplorerException("Failed to query balance", t);
            }
        }

        public static BigDecimal queryBalance(String address) throws NKNExplorerException {
            try {
                final HashMap<String, String> params = new HashMap<>();
                params.put("address", address);
                final JSONObject result = (JSONObject) ConnectionProvider.attempt((bootstrapNode) -> HttpApi.rpcRequest(bootstrapNode, "getbalancebyaddr", params));
                return result.getBigDecimal("amount");
            } catch (Exception t) {
                if (t instanceof NKNExplorerException) throw (NKNExplorerException) t;
                throw new NKNExplorerException("Failed to query balance", t);
            }
        }

        public static long getNonce(String address) throws NKNExplorerException {
            try {
                final HashMap<String, String> params = new HashMap<>();
                params.put("address", address);
                final JSONObject response = (JSONObject) ConnectionProvider.attempt((node) -> HttpApi.rpcRequest(node, "getnoncebyaddr", params));

                long nonce = response.getJSONObject("result").getLong("nonce");
                if (response.getJSONObject("result").has("nonceInTxPool")) {
                    nonce = Math.max(nonce, response.getJSONObject("result").getLong("nonceInTxPool"));
                }
                return nonce;
            } catch (Exception t) {
                if (t instanceof NKNExplorerException) throw (NKNExplorerException) t;
                throw new NKNExplorerException("Failed to query nonce", t);
            }
        }
    }

    public static class BlockChain {
        private BlockChain() {}

        public static int getBlockCount() throws NKNExplorerException {
            try {
                return (int)ConnectionProvider.attempt((node) -> HttpApi.rpcRequest(node, "getblockcount"));
            } catch (Exception t) {
                if (t instanceof NKNExplorerException) throw (NKNExplorerException) t;
                throw new NKNExplorerException("Failed to query block count", t);
            }
        }


        public static LatestBlockHash getLatestBlockHash() throws NKNExplorerException {
            try {
                final JSONObject result = (JSONObject) ConnectionProvider.attempt((node) -> HttpApi.rpcRequest(node, "getlatestblockhash"));
                return new LatestBlockHash(result.getString("hash"), result.getInt("height"));
            } catch (Exception t) {
                if (t instanceof NKNExplorerException) throw (NKNExplorerException) t;
                throw new NKNExplorerException("Failed to query block hash", t);
            }
        }

        public static final class LatestBlockHash {
            public final String hash;
            public final int height;

            public LatestBlockHash(String hash, int height) {
                this.hash = hash;
                this.height = height;
            }
        }
    }

    public static class Subscription {
        private Subscription() {}

        public static int getFirstAvailableTopicBucket(String topic) throws NKNExplorerException {
            try {
                final HashMap<String, String> params = new HashMap<>();
                params.put("topic", topic);
                return (int) ConnectionProvider.attempt((node) -> HttpApi.rpcRequest(node, "getfirstavailabletopicbucket", params));
            } catch (Exception t) {
                if (t instanceof NKNExplorerException) throw (NKNExplorerException) t;
                throw new NKNExplorerException("Failed to query first available topic bucket", t);
            }
        }

        public static int getTopicBucketsCount(String topic) throws NKNExplorerException {
            try {
                final HashMap<String, String> params = new HashMap<>();
                params.put("topic", topic);
                return (int) ConnectionProvider.attempt((node) -> HttpApi.rpcRequest(node, "gettopicbucketscount", params));
            } catch (Exception t) {
                if (t instanceof NKNExplorerException) throw (NKNExplorerException) t;
                throw new NKNExplorerException("Failed to query topic buckets count", t);
            }
        }

        public static Subscriber[] getSubscribers(String topic, int bucket) throws NKNExplorerException {
            try {
                final HashMap<String, String> params = new HashMap<>();
                params.put("topic", topic);
                params.put("bucket", Integer.toString(bucket));
                final JSONObject result = (JSONObject) ConnectionProvider.attempt((node) -> HttpApi.rpcRequest(node, "getsubscribers", params));

                int i = 0;
                final Subscriber[] subscribers = new Subscriber[result.length()];
                for (String id : result.keySet()) {
                    subscribers[i++] = new Subscriber(id, result.getString(id));
                }

                return subscribers;
            } catch (Exception t) {
                if (t instanceof NKNExplorerException) throw (NKNExplorerException) t;
                throw new NKNExplorerException("Failed to query subscribers", t);
            }
        }


        public static final class Subscriber {
            public final String fullClientIdentifier;
            public final String meta;

            public Subscriber(String fullClientIdentifier, String meta) {
                this.fullClientIdentifier = fullClientIdentifier;
                this.meta = meta;
            }
        }
    }

}
