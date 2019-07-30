package jsmith.nknsdk.client;

import jsmith.nknsdk.network.ConnectionProvider;
import jsmith.nknsdk.network.HttpApi;
import jsmith.nknsdk.utils.Base58;
import jsmith.nknsdk.utils.Crypto;
import jsmith.nknsdk.wallet.WalletException;
import jsmith.nknsdk.wallet.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 *
 */
public class NKNExplorer {

    private static final Logger LOG = LoggerFactory.getLogger(NKNExplorer.class);

    public static BigDecimal queryBalance(String address) throws WalletException {
        try {
            return ConnectionProvider.attempt((bootstrapNode) -> HttpApi.getBalance(bootstrapNode, address));
        } catch (Exception t) {
            if (t instanceof WalletException) throw (WalletException) t;
            throw new WalletException("Failed to query balance", t);
        }
    }

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

    public static String resolveNamedAddress(String name) throws WalletException {
        // https://github.com/nknorg/nkn/blob/master/api/common/interfaces.go#L1070
        try {
            return ConnectionProvider.attempt((bootstrapNode) -> HttpApi.resolveName(bootstrapNode, name));
        } catch (Exception t) {
            if (t instanceof WalletException) throw (WalletException) t;
            throw new WalletException("Failed to query balance", t);
        }
    }
    
    public static int getBlockCount() throws WalletException {
        try {
            return ConnectionProvider.attempt((bootstrapNode) -> HttpApi.getBlockCount(bootstrapNode));
        } catch (Exception t) {
            if (t instanceof WalletException)
                throw (WalletException) t;
            throw new WalletException("Failed to query block count", t);
        }
    }

    public static int getFirstAvailableTopicBucket(String topic) throws WalletException {
        try {
            return ConnectionProvider.attempt((bootstrapNode) -> HttpApi.getFirstAvailableTopicBucket(bootstrapNode, topic));
        } catch (Exception t) {
            if (t instanceof WalletException)
                throw (WalletException) t;
            throw new WalletException("Failed to query first available topic bucket", t);
        }
    }
    
    public static NKNExplorer.GetLatestBlockHashResult getLatestBlockHash() throws WalletException {
        try {
            return ConnectionProvider.attempt((bootstrapNode) -> HttpApi.getLatestBlockHash(bootstrapNode));
        } catch (Exception t) {
            if (t instanceof WalletException)
                throw (WalletException) t;
            throw new WalletException("Failed to query latest block hash", t);
        }
    }

    public static int getTopicBucketsCount(String topic) throws WalletException {
        try {
            return ConnectionProvider.attempt((bootstrapNode) -> HttpApi.getTopicBucketsCount(bootstrapNode, topic));
        } catch (Exception t) {
            if (t instanceof WalletException)
                throw (WalletException) t;
            throw new WalletException("Failed to query topic buckets count", t);
        }
    }

    public static Subscriber[] getSubscribers(String topic, int bucket) throws WalletException {
        try {
            return ConnectionProvider.attempt((bootstrapNode) -> HttpApi.getSubscribers(bootstrapNode, topic, bucket));
        } catch (Exception t) {
            if (t instanceof WalletException) throw (WalletException) t;
            throw new WalletException("Failed to query subscribers", t);
        }
    }

    public static final class GetLatestBlockHashResult {
        public final String hash;
        public final int height;

        public GetLatestBlockHashResult(String hash, int height) {
            this.hash = hash;
            this.height = height;
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
