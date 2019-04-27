package jsmith.nknsdk.wallet;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.client.NKNExplorer;
import jsmith.nknsdk.network.ConnectionProvider;
import jsmith.nknsdk.network.HttpApi;
import jsmith.nknsdk.wallet.transactions.NameServiceT;
import jsmith.nknsdk.wallet.transactions.TransactionT;
import jsmith.nknsdk.wallet.transactions.TransferToT;

import java.math.BigDecimal;

/**
 *
 */
public class NKNTransaction {

    private final Wallet w;
    public NKNTransaction(Wallet wallet) {
        this.w = wallet;
    }

    public String registerName(String name) throws WalletException {
        return registerName(name, BigDecimal.ZERO);
    }

    public String registerName(String name, BigDecimal fee) throws WalletException {
        final NameServiceT nameServiceT = new NameServiceT();

        nameServiceT.setName(name);
        nameServiceT.setPublicKey(ByteString.copyFrom(w.getPublicKey()));
        nameServiceT.setNameServiceType(NameServiceT.NameServiceType.REGISTER);

        return submitTransaction(nameServiceT, fee);
    }

    public String deleteName(String name) throws WalletException {
        return deleteName(name, BigDecimal.ZERO);
    }

    public String deleteName(String name, BigDecimal fee) throws WalletException {
        final NameServiceT nameServiceT = new NameServiceT();

        nameServiceT.setName(name);
        nameServiceT.setPublicKey(ByteString.copyFrom(w.getPublicKey()));
        nameServiceT.setNameServiceType(NameServiceT.NameServiceType.DELETE);

        return submitTransaction(nameServiceT, fee);
    }

    public String transferTo(String toAddress, BigDecimal amount) throws WalletException {
        return transferTo(toAddress, amount, BigDecimal.ZERO);
    }

    public String transferTo(String toAddress, BigDecimal amount, BigDecimal fee) throws WalletException {
        if (!NKNExplorer.isAddressValid(toAddress)) throw new WalletException("Transaction failed: Target address is not valid");

        final TransferToT transferToT = new TransferToT();

        transferToT.setSenderProgramHash(w.getProgramHash());
        transferToT.setRecipientAddress(toAddress);
        transferToT.setAmountLongValue(amount.multiply(new BigDecimal(Asset.T_NKN.mul)).longValue());

        return submitTransaction(transferToT, fee);
    }





    private String submitTransaction(TransactionT tx, BigDecimal fee) throws WalletException {
        tx.setNonce(nextNonce());
        tx.setFeeInLongValue(fee.multiply(new BigDecimal(Asset.T_NKN.mul)).longValue());

        return w.submitTransaction(tx);
    }

    private long nextNonce() throws WalletException {
        long nonce;

        try {
            nonce = ConnectionProvider.attempt((node) -> HttpApi.getNonce(node, w.getAddress(), Asset.T_NKN));
        } catch (Throwable t) {
            if (t instanceof WalletException) throw (WalletException) t;
            throw new WalletException("Transaction failed: Failed to query nonce", t);
        }

        return nonce;
    }
}
