package jsmith.nknsdk.wallet;

import jsmith.nknsdk.client.NKNExplorer;
import jsmith.nknsdk.network.ConnectionProvider;
import jsmith.nknsdk.network.HttpApi;
import jsmith.nknsdk.wallet.transactions.TransferToT;

import java.math.BigDecimal;

/**
 *
 */
public class NKNTransactions {

    public static String transferTo(Wallet w, String toAddress, BigDecimal amount) throws WalletException {
        return transferTo(w, toAddress, amount, BigDecimal.ZERO);
    }

    public static String transferTo(Wallet w, String toAddress, BigDecimal amount, BigDecimal fee) throws WalletException {
        if (!NKNExplorer.isAddressValid(toAddress)) throw new WalletException("Transaction failed: Target address is not valid");
        long nonce;

        try {
            nonce = ConnectionProvider.attempt((node) -> HttpApi.getNonce(node, w.getAddress(), Asset.T_NKN));
        } catch (Throwable t) {
            if (t instanceof WalletException) throw (WalletException) t;
            throw new WalletException("Transaction failed: Failed to query nonce", t);
        }

        final TransferToT transferToT = new TransferToT();

        transferToT.setSenderProgramHash(w.getProgramHash());
        transferToT.setRecipientAddress(toAddress);
        transferToT.setAmountLongValue(amount.multiply(new BigDecimal(Asset.T_NKN.mul)).longValue());

        transferToT.setNonce(nonce);
        transferToT.setFeeInLongValue(fee.multiply(new BigDecimal(Asset.T_NKN.mul)).longValue());

        return w.submitTransaction(transferToT);
    }

}
