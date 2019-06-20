package jsmith.nknsdk.wallet;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.utils.Base58;
import org.bouncycastle.util.encoders.Hex;

import static jsmith.nknsdk.utils.Crypto.*;

/**
 *
 */
public class WalletUtils {

    public static final byte[] ADDRESS_PREFIX = new byte[]{ 0x02, (byte) 0xb8, 0x25 }; // NKN in Base58

    public static String getProgramHashAsHexString(String addressStr) {
        final byte[] address = Base58.decode(addressStr);
        final byte[] programHash = new byte[address.length - 4 - ADDRESS_PREFIX.length];
        System.arraycopy(address, ADDRESS_PREFIX.length, programHash, 0, programHash.length);
        return Hex.toHexString(programHash);
    }

    public static byte[] getProgramHashAsByteArray(String addressStr) {
        final byte[] address = Base58.decode(addressStr);
        final byte[] programHash = new byte[address.length - 4 - ADDRESS_PREFIX.length];
        System.arraycopy(address, ADDRESS_PREFIX.length, programHash, 0, programHash.length);
        return programHash;
    }

    public static ByteString getProgramHashAsByteString(String addressStr) {
        final byte[] address = Base58.decode(addressStr);
        final byte[] programHash = new byte[address.length - 4 - ADDRESS_PREFIX.length];
        System.arraycopy(address, ADDRESS_PREFIX.length, programHash, 0, programHash.length);
        return ByteString.copyFrom(programHash);
    }

    public static String getAddressFromProgramHash(ByteString programHash) {
        byte[] sh = ByteString.copyFrom(ADDRESS_PREFIX).concat(programHash).toByteArray();
        final byte[] x = doubleSha256(sh);

        final byte[] enc = new byte[sh.length + 4];
        System.arraycopy(sh, 0, enc, 0, sh.length);
        System.arraycopy(x, 0, enc, sh.length, 4);

        return Base58.encode(enc);
    }

    public static String getAddressFromProgramHash(byte[] programHash) {
        return getAddressFromProgramHash(ByteString.copyFrom(programHash));
    }

    public static byte[] getProgramHashFromPublicKey(byte[] publicKey) {
        return r160(sha256(getSignatureRedeemFromPublicKey(publicKey)));
    }


    public static byte[] getSignatureRedeemFromPublicKey(byte[] publicKey) {
        if (publicKey == null || publicKey.length != 32) throw new IllegalArgumentException("Not a valid public key was provided");

        final byte[] redeem = new byte[1 + publicKey.length + 1];

        redeem[0] = 0x20;
        redeem[redeem.length - 1] = (byte) 0xAC;
        System.arraycopy(publicKey, 0, redeem, 1, publicKey.length);

        return redeem;
    }

}
