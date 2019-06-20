 package jsmith.nknsdk.wallet.transactions;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.network.proto.TransactionpayloadP;
import jsmith.nknsdk.wallet.WalletUtils;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;

 /**
 *
 */
public class TransferToT extends TransactionT {

    private ByteString recipientProgramHash;
    private ByteString senderProgramHash;
    private long amountLongValue = 0;


    public long getAmountInLongValue() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return amountLongValue;
    }

    public void setAmountLongValue(long amountLongValue) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.amountLongValue = amountLongValue;
    }

    public ByteString getRecipientProgramHash() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return recipientProgramHash;
    }

    public String getRecipientAddress() {
        return WalletUtils.getAddressFromProgramHash(getRecipientProgramHash());
    }

    public void setRecipientProgramHash(ByteString programHash) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.recipientProgramHash = programHash;
    }

    public void setRecipientAddress(String address) {
        setRecipientProgramHash(WalletUtils.getProgramHashAsByteString(address));
    }

    public ByteString getSenderProgramHash() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return senderProgramHash;
    }

    public String getSenderAddress() {
        return WalletUtils.getAddressFromProgramHash(getSenderProgramHash());
    }

    public void setSenderProgramHash(ByteString programHash) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.senderProgramHash = programHash;
    }

    public void setSenderAddress(String address) {
        setSenderProgramHash(WalletUtils.getProgramHashAsByteString(address));
    }



    @Override
    public ByteString build(EdDSAPrivateKey privateKey, ByteString signatureRedeem) {

        final TransactionpayloadP.TransferAsset.Builder txAsset = TransactionpayloadP.TransferAsset.newBuilder();
        txAsset.setSender(senderProgramHash);
        txAsset.setRecipient(recipientProgramHash);
        txAsset.setAmount(amountLongValue);

        final TransactionpayloadP.TransactionPayload.Builder txPayload = TransactionpayloadP.TransactionPayload.newBuilder();
        txPayload.setType(TransactionpayloadP.TransactionPayloadType.TransferAssetType);
        txPayload.setData(txAsset.build().toByteString());

        payload = txPayload.build();

        return super.build(privateKey, signatureRedeem);
    }


}
