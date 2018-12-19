package jsmith.nknclient.network;

/**
 *
 */
public abstract class Packet {


    public abstract PayloadType getPayloadType();

    @Override
    public abstract String toString();

    public enum PayloadType {
        BINARY (0),
        TEXT (1),
        ACK (2);


        private final int code;

        PayloadType(int code) {
            this.code = code;
        }

        public int getCode() {
            return this.code;
        }
    }

}
