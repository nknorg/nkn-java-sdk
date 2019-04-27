package jsmith.nknsdk.wallet.transactions;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.network.proto.TransactionpayloadP;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;

/**
 *
 */
public class NameServiceT extends TransferToT {

    private String name;
    private ByteString publicKey;
    private NameServiceType type;

    public String getName() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return name;
    }

    public void setName(String name) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.name = name;
    }

    public ByteString getPublicKey() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return publicKey;
    }

    public void setPublicKey(ByteString publicKey) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.publicKey = publicKey;
    }

    public NameServiceType getNameServiceType() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return type;
    }

    public void setNameServiceType(NameServiceType type) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.type = type;
    }

    @Override
    public ByteString build(EdDSAPrivateKey privateKey, ByteString signatureRedeem) {

        final TransactionpayloadP.RegisterName.Builder txName = TransactionpayloadP.RegisterName.newBuilder();
        txName.setName(name);
        txName.setRegistrant(publicKey);

        final TransactionpayloadP.TransactionPayload.Builder txPayload = TransactionpayloadP.TransactionPayload.newBuilder();
        txPayload.setType(type.protoTxType);
        txPayload.setData(txName.build().toByteString());

        payload = txPayload.build();

        return super.build(privateKey, signatureRedeem);
    }

    public enum NameServiceType {
        REGISTER (TransactionpayloadP.TransactionPayloadType.RegisterNameType),
        DELETE (TransactionpayloadP.TransactionPayloadType.DeleteNameType);



        private final TransactionpayloadP.TransactionPayloadType protoTxType;

        NameServiceType(TransactionpayloadP.TransactionPayloadType protoTxType) {
            this.protoTxType = protoTxType;
        }
    }

}
