package jsmith.nknclient.wallet;

import jsmith.nknclient.utils.Base58;
import jsmith.nknclient.utils.Crypto;
import org.bouncycastle.util.encoders.Hex;

import java.security.PrivateKey;

/**
 *
 */
public class WalletUtils {

    public static String transferSignature(String baseTransferRawString, PrivateKey privateKey) {
        final byte[] transferBytes = Hex.decode(baseTransferRawString);

        final String signature = Hex.toHexString(Crypto.sha256andSign(privateKey, transferBytes));
        System.out.println(signature);

        final String signatureCount = "01";
        final String signatureStructLength = "41";
        final String signatureLength = "40";

        return signatureCount + signatureStructLength + signatureLength + signature;
    }

    public static String getProgramHashAsHexString(String addressStr) {
        final byte[] address = Base58.decode(addressStr);
        final byte[] programHash = new byte[address.length - 5];
        System.arraycopy(address, 1, programHash, 0, programHash.length);
        return Hex.toHexString(programHash);
    }

}
