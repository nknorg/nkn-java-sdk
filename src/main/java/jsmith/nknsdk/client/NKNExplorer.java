package jsmith.nknsdk.client;

import jsmith.nknsdk.network.ConnectionProvider;
import jsmith.nknsdk.network.HttpApi;
import jsmith.nknsdk.utils.Base58;
import jsmith.nknsdk.utils.Crypto;
import jsmith.nknsdk.wallet.Asset;
import jsmith.nknsdk.wallet.WalletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 *
 */
public class NKNExplorer {

    private static final Logger LOG = LoggerFactory.getLogger(NKNExplorer.class);


    public static BigDecimal queryBalance(Asset asset, String address) throws WalletException {
        try {
            return ConnectionProvider.attempt((bootstrapNode) -> HttpApi.getSumUTXO(bootstrapNode, address, asset == null ? Asset.T_NKN : asset));
        } catch (Exception t) {
            if (t instanceof WalletException) throw (WalletException) t;
            throw new WalletException("Failed to query balance", t);
        }
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
