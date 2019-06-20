package jsmith.nknsdk.wallet.transactions;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.network.proto.TransactionP;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;

/**
 *
 */
public class NameServiceT extends TransactionT {

    private String name;
    private ByteString publicKey;
    private NameServiceType type = NameServiceType.REGISTER;

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

        final TransactionP.RegisterName.Builder txName = TransactionP.RegisterName.newBuilder();
        txName.setName(name);
        txName.setRegistrant(publicKey);

        final TransactionP.TransactionPayload.Builder txPayload = TransactionP.TransactionPayload.newBuilder();
        txPayload.setType(type.protoTxType);
        txPayload.setData(txName.build().toByteString());

        payload = txPayload.build();

        return super.build(privateKey, signatureRedeem);
    }

    public enum NameServiceType {
        REGISTER (TransactionP.TransactionPayloadType.REGISTER_NAME_TYPE),
        DELETE (TransactionP.TransactionPayloadType.DELETE_NAME_TYPE);


        private final TransactionP.TransactionPayloadType protoTxType;

        NameServiceType(TransactionP.TransactionPayloadType protoTxType) {
            this.protoTxType = protoTxType;
        }
    }

}
