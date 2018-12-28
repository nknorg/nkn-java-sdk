package jsmith.nknclient.wallet.transactions;

import jsmith.nknclient.utils.Crypto;

/**
 *
 */
public abstract class Attribute {

    final byte type;

    public Attribute(byte type) {
        this.type = type;
    }

    public abstract byte[] getData();



    public static class Nonce extends Attribute {

        final byte[] data;

        public Nonce() {
            super((byte) 0x00);
            this.data = Crypto.nextRandom32B();
        }

        @Override
        public byte[] getData() {
            return data;
        }
    }

}

