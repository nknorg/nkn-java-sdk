package jsmith.nknclient.wallet.transactions;

import jsmith.nknclient.wallet.Asset;
import jsmith.nknclient.wallet.AssetTransfer;
import jsmith.nknclient.wallet.WalletError;
import jsmith.nknclient.wallet.WalletUtils;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Vector;

/**
 *
 */
public class TransactionUtils {


    public static String genTxInputsAndOutputs(Asset asset, JSONArray utxoList, String walletProgramHash, AssetTransfer... targets) {
        BigDecimal targetValue = new BigDecimal(0);
        for (AssetTransfer target : targets) {
            targetValue = targetValue.add(target.amount);
        }

        final StringBuilder inputRaw = new StringBuilder();
        final StringBuilder outputRaw = new StringBuilder();
        String outputsLength, inputsLength = "";

        BigDecimal value = new BigDecimal(0);

        boolean notEnough = true;
        for (int i = 0; utxoList != null && i < utxoList.length(); i++) {
            final JSONObject utxo = utxoList.getJSONObject(i);

            final BigDecimal thisInputVal = utxo.getBigDecimal("Value");
            final String txId = utxo.getString("Txid");
            value = value.add(thisInputVal);

            inputRaw
                    .append(reverseHexStr(txId))
                    .append(genInputIndexRawString(utxo.getInt("Index")));

            if (value.compareTo(targetValue) > 0) {
                notEnough = false;
                inputsLength = rawTxLengthString(i + 1);
                break;
            }
        }

        if (notEnough) {
            throw new WalletError("Not enough assets to transfer");
        }

        final String assetIdString = reverseHexStr(asset.ID);

        for (AssetTransfer target : targets) {
            StringBuilder outputValString = new StringBuilder(reverseHexStr(target.amount.multiply(new BigDecimal(asset.mul)).toBigInteger().toString(16)));
            while (outputValString.length() < 16) {
                outputValString.append("0");
            }

            outputRaw
                    .append(assetIdString)
                    .append(outputValString.toString())
                    .append(WalletUtils.getProgramHashAsHexString(target.toAddress));
        }

        if (value.compareTo(targetValue) > 0) {
            final BigDecimal change = value.subtract(targetValue);
            StringBuilder changeString = new StringBuilder(reverseHexStr(change.multiply(new BigDecimal(asset.mul)).toBigInteger().toString(16)));
            while (changeString.length() < 16) {
                changeString.append("0");
            }

            outputRaw
                    .append(assetIdString)
                    .append(changeString.toString())
                    .append(walletProgramHash);

            outputsLength = rawTxLengthString(targets.length + 1);
        } else {
            outputsLength = rawTxLengthString(targets.length);
        }

        return inputsLength + inputRaw.toString() + outputsLength + outputRaw.toString();
    }


    public static String rawTxLengthString(int length) {
        final StringBuilder lengthHexSb = new StringBuilder();

        if (length < 253 ) { // 0xFD
            // Do nothing
        } else if (length < 65535) { // 0xFFFF
            lengthHexSb.append("FD");
        } else { // 0xFFFFFFFF
            lengthHexSb.append("FE");
        } // if even more than 0xFFFFFFFF we would have to use longs and it would change a lot of things along the way

        return lengthHexSb.toString() + reverseHexStr(Long.toHexString(length));
    }

    public static String genInputIndexRawString(int index) {
        String inputString = Integer.toHexString(index);
        if (inputString.length() % 2 == 1) inputString = "0" + inputString;
        if(inputString.length() > 2) {
            return reverseHexStr(inputString);
        } else {
            return inputString + "00";
        }
    }

    public static String reverseHexStr(String hex) {
        if (hex.length() % 2 == 1) hex = "0" + hex;

        final Vector<String> vec = new Vector<>();
        final StringBuilder hexSb = new StringBuilder();
        for(int i = 0; i < hex.length(); i += 2) {
            vec.add(hex.substring(i, i + 2));
        }

        for(int i = vec.size() - 1; i >= 0; i--) {
            hexSb.append(vec.get(i));
        }
        return hexSb.toString();
    }

    public static String rawBaseTransfer(String txDescription, String inputsAndOutputs) {

        final byte txType = TxType.TRANSFER_ASSET.type;
        final byte payloadVersion = 0;

        Attribute[] attributes = new Attribute[] {
                new Attribute.Nonce()
        };
        if (txDescription != null) {
            attributes = new Attribute[] {
                    new Attribute.Nonce(),
                    new Attribute.Description(txDescription)
            };
        }

        final StringBuilder attrRawString = new StringBuilder(rawTxLengthString(attributes.length));
        for (Attribute attr : attributes) {
            final byte[] data = attr.getData();

            attrRawString
                    .append(Hex.toHexString(new byte[] { attr.type }))
                    .append(rawTxLengthString(data.length))
                    .append(Hex.toHexString(data));
        }

        return Hex.toHexString(new byte[] { txType, payloadVersion }) + attrRawString + inputsAndOutputs;
    }



}
