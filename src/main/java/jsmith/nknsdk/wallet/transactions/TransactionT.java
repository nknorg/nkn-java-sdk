package jsmith.nknsdk.wallet.transactions;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.network.proto.TransactionP;
import jsmith.nknsdk.utils.Crypto;
import jsmith.nknsdk.utils.EncodeUtils;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;

/**
 *
 */
public abstract class TransactionT {

    protected boolean dirty = true;
    protected boolean readonly = false;

    private long nonce;
    private long feeLongValue;

    public long getFeeInLongValue() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return feeLongValue;
    }

    public long getNonce() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return nonce;
    }

    public void setFeeInLongValue(long feeLongValue) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.feeLongValue = feeLongValue;
    }

    public void setNonce(long nonce) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.nonce = nonce;
    }

    public boolean isReadonly() {
        return readonly;
    }

    void makeReadonly() {
        dirty = false;
        readonly = true;
    }


    protected TransactionP.TransactionPayload payload;


    public ByteString build(EdDSAPrivateKey privateKey, ByteString signatureRedeem) {
        final TransactionP.UnsignedTx.Builder unsignedTx = TransactionP.UnsignedTx.newBuilder();
        unsignedTx.setPayload(payload);
        unsignedTx.setNonce(nonce);
        unsignedTx.setFee(feeLongValue);

        final TransactionP.MsgTx.Builder msgTxBuilder = TransactionP.MsgTx.newBuilder();
        msgTxBuilder.setUnsignedTx(unsignedTx.build());
        msgTxBuilder.addPrograms(sign(privateKey, signatureRedeem));

        final ByteString data = msgTxBuilder.build().toByteString();

        makeReadonly();
        return data;
    }

    private TransactionP.Program sign(EdDSAPrivateKey privateKey, ByteString signatureRedeem) {
        final ByteString dataToSign = ByteString.EMPTY
                .concat(EncodeUtils.encodeUint32(payload.getTypeValue()))
                .concat(EncodeUtils.encodeBytes(payload.getData()))
                .concat(EncodeUtils.encodeUint64(nonce))
                .concat(EncodeUtils.encodeUint64(feeLongValue))
                .concat(EncodeUtils.encodeBytes(ByteString.EMPTY)); // unsignedTx.attributes

        final byte[] sig = Crypto.sha256andSign(privateKey, dataToSign.toByteArray());
        final TransactionP.Program.Builder programBuilder = TransactionP.Program.newBuilder();
        programBuilder.setCode(signatureRedeem);
        programBuilder.setParameter(EncodeUtils.encodeUint(sig.length).concat(ByteString.copyFrom(sig)));

        return programBuilder.build();

    }


}
