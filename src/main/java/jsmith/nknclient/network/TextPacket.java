package jsmith.nknclient.network;

/**
 *
 */
public class TextPacket extends Packet {

    private final String message;

    public TextPacket(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public PayloadType getPayloadType() {
        return PayloadType.TEXT;
    }

    @Override
    public String toString() {
        return "TextPacket";
    }
}
