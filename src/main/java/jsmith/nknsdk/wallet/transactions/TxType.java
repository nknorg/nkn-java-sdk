package jsmith.nknsdk.wallet.transactions;

/**
 *
 */
public enum TxType {

    TRANSFER_ASSET(0x10),
    SUBSCRIBE(0x60);

    public final byte type;

    TxType(int type) {
        this.type = (byte)type;
    }

}
