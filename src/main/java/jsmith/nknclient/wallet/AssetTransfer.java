package jsmith.nknclient.wallet;

import jsmith.nknclient.client.NKNExplorer;

import java.math.BigDecimal;

/**
 *
 */
public class AssetTransfer {

    public final String toAddress;
    public final BigDecimal amount;
    public final String message;

    public AssetTransfer(String toAddress, BigDecimal amount) {
        this(toAddress, amount, null);
    }

    public AssetTransfer(String toAddress, BigDecimal amount, String message) {
        if (!NKNExplorer.isAddressValid(toAddress)) throw new WalletError("Receiving address is not valid NKN address");
        this.toAddress = toAddress;
        this.amount = amount;
        this.message = message;
    }

}
