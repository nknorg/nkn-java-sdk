package jsmith.nknclient.client;

import com.darkyen.dave.WebbException;
import jsmith.nknclient.network.ConnectionProvider;
import jsmith.nknclient.network.HttpApi;
import jsmith.nknclient.utils.Base58;
import jsmith.nknclient.utils.Crypto;
import jsmith.nknclient.wallet.Asset;
import jsmith.nknclient.wallet.WalletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.InetSocketAddress;

/**
 *
 */
public class NKNExplorer {

    private static final Logger LOG = LoggerFactory.getLogger(NKNExplorer.class);


    public static BigDecimal queryBalance(Asset asset, String address) throws WalletException {
        int retries = 0;
        BigDecimal result;
        WebbException error = null;
        do {
            final InetSocketAddress bootstrapNode = ConnectionProvider.nextNode(retries++);
            if (bootstrapNode != null) {
                try {
                    result = HttpApi.getSumUTXO(bootstrapNode, address, asset == null ? Asset.T_NKN : asset);
                    return result;
                } catch (WebbException e) {
                    error = e;
                    LOG.warn("Query balance RPC request failed");
                }
            }
        } while (retries >= 0);

        throw new WalletException("Failed to query balance", error);
    }
    public static BigDecimal queryBalance(String address) throws WalletException {
        return queryBalance(Asset.T_NKN, address);
    }


    public static boolean isAddressValid(String address) {
        if (address.length() != 34) return false;
        try {

            final byte[] addressBytes = Base58.decode(address);
            if (addressBytes[0] != 53) return false;

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

}
