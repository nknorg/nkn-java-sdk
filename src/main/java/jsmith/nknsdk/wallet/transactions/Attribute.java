package jsmith.nknsdk.wallet.transactions;

import jsmith.nknsdk.utils.Crypto;

import java.nio.charset.Charset;

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

    public static class Description extends Attribute {

        final byte[] data;

        public Description(String description) {
            super((byte) 0x90);
            this.data = description.getBytes(Charset.forName("UTF-8"));
        }

        @Override
        public byte[] getData() {
            return data;
        }
    }

}

