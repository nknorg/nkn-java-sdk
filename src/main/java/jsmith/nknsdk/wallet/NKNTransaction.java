package jsmith.nknsdk.wallet;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.client.NKNExplorer;
import jsmith.nknsdk.client.NKNExplorerException;
import jsmith.nknsdk.wallet.transactions.NameServiceT;
import jsmith.nknsdk.wallet.transactions.SubscriptionT;
import jsmith.nknsdk.wallet.transactions.TransactionT;
import jsmith.nknsdk.wallet.transactions.TransferToT;

import java.math.BigDecimal;

/**
 *
 */
public class NKNTransaction {

    private final Wallet w;
    NKNTransaction(Wallet wallet) {
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
        transferToT.setAmountLongValue(amount.multiply(new BigDecimal(100000000)).longValue());

        return submitTransaction(transferToT, fee);
    }

    public String subscribe(String topic, int duration) throws WalletException {
        return subscribe(topic, duration, BigDecimal.ZERO);
    }
    public String subscribe(String topic, int duration, String clientIdentifier) throws WalletException {
        return subscribe(topic, duration, clientIdentifier, BigDecimal.ZERO);
    }
    public String subscribe(String topic, int duration, String clientIdentifier, String meta) throws WalletException {
        return subscribe(topic, duration, clientIdentifier, meta, BigDecimal.ZERO);
    }

    public String subscribe(String topic, int duration, BigDecimal fee) throws WalletException {
        return subscribe(topic, duration, null, null, fee);
    }
    public String subscribe(String topic, int duration, String clientIdentifier, BigDecimal fee) throws WalletException {
        return subscribe(topic, duration, clientIdentifier, null, fee);
    }

    public String subscribe(String topic, int duration, String clientIdentifier, String meta, BigDecimal fee) throws WalletException {
        final SubscriptionT subscriptionT = new SubscriptionT();

        subscriptionT.setPublicKey(ByteString.copyFrom(w.getPublicKey()));
        subscriptionT.setTopic(topic);
        subscriptionT.setDuration(duration);
        subscriptionT.setIdentifier(clientIdentifier == null ? "" : clientIdentifier);
        subscriptionT.setMeta(meta == null ? "" : meta);

        return submitTransaction(subscriptionT, fee);
    }

    public String unsubscribe(String topic, String clientIdentifier) throws WalletException {
        return unsubscribe(topic, clientIdentifier, BigDecimal.ZERO);
    }

    public String unsubscribe(String topic, String clientIdentifier, BigDecimal fee) throws WalletException {
        final SubscriptionT subscriptionT = new SubscriptionT();

        subscriptionT.setPublicKey(ByteString.copyFrom(w.getPublicKey()));
        subscriptionT.setTopic(topic);
        subscriptionT.setIdentifier(clientIdentifier == null ? "" : clientIdentifier);
        subscriptionT.setActionType(SubscriptionT.SubscriptionActionType.UNSUBSCRIBE);

        return submitTransaction(subscriptionT, fee);
    }



    private String submitTransaction(TransactionT tx, BigDecimal fee) throws WalletException {
        try {
            tx.setNonce(NKNExplorer.Wallet.getNonce(w.getAddress()));
            tx.setFeeInLongValue(fee.multiply(new BigDecimal(100000000)).longValue());

            return w.submitTransaction(tx);
        } catch (NKNExplorerException e) {
            throw new WalletException("Failed to query current nonce", e);
        }
    }

    public String customTransaction(TransactionT tx) throws WalletException {
        return customTransaction(tx, BigDecimal.ZERO);
    }
    public String customTransaction(TransactionT tx, BigDecimal fee) throws WalletException {
        return submitTransaction(tx, fee);
    }
}
