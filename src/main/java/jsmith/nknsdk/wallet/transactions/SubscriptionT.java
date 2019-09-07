package jsmith.nknsdk.wallet.transactions;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.network.proto.TransactionP;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;

/**
 *
 */
public class SubscriptionT extends TransactionT {

    private ByteString publicKey;
    private String topic;
    private int duration = 1;
    private String identifier = "";
    private String meta = "";
    private SubscriptionActionType type = SubscriptionActionType.SUBSCRIBE;

    public String getTopic() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return topic;
    }

    public void setTopic(String topic) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.topic = topic;
    }

    public ByteString getPublicKey() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return publicKey;
    }

    public void setPublicKey(ByteString publicKey) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.publicKey = publicKey;
    }

    public int getDuration() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return duration;
    }

    public void setDuration(int duration) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.duration = duration;
    }

    public String getIdentifier() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return identifier;
    }

    public void setIdentifier(String identifier) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.identifier = identifier;
    }

    public String getMeta() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return meta;
    }

    public void setMeta(String meta) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.meta = meta;
    }

    public SubscriptionActionType getActionType() {
        if (dirty) throw new IllegalStateException("Transaction is in dirty state");
        return type;
    }

    public void setActionType(SubscriptionActionType type) {
        if (readonly) throw new IllegalStateException("Transaction is read only");
        this.type = type;
    }

    @Override
    public ByteString build(EdDSAPrivateKey privateKey, ByteString signatureRedeem) {

        ByteString txByteString;

        if (type == SubscriptionActionType.SUBSCRIBE) {
            final TransactionP.Subscribe.Builder txSub = TransactionP.Subscribe.newBuilder();
            txSub.setSubscriber(publicKey);
            txSub.setIdentifier(identifier);
            txSub.setTopic(topic);

            txSub.setDuration(duration);
            txSub.setMeta(meta);

            txByteString = txSub.build().toByteString();
        } else {
            final TransactionP.Unsubscribe.Builder txSub = TransactionP.Unsubscribe.newBuilder();
            txSub.setSubscriber(publicKey);
            txSub.setIdentifier(identifier);
            txSub.setTopic(topic);

            txByteString = txSub.build().toByteString();
        }

        final TransactionP.TransactionPayload.Builder txPayload = TransactionP.TransactionPayload.newBuilder();
        txPayload.setType(type.protoTxType);
        txPayload.setData(txByteString);

        payload = txPayload.build();

        return super.build(privateKey, signatureRedeem);
    }

    public enum SubscriptionActionType {
        SUBSCRIBE (TransactionP.TransactionPayloadType.SUBSCRIBE_TYPE),
        UNSUBSCRIBE (TransactionP.TransactionPayloadType.UNSUBSCRIBE_TYPE);


        private final TransactionP.TransactionPayloadType protoTxType;

        SubscriptionActionType(TransactionP.TransactionPayloadType protoTxType) {
            this.protoTxType = protoTxType;
        }
    }

}
