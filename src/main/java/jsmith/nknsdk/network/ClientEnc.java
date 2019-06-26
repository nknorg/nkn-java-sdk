package jsmith.nknsdk.network;

import com.google.protobuf.ByteString;
import com.iwebpp.crypto.TweetNaclFast;
import jsmith.nknsdk.client.NKNClientException;
import jsmith.nknsdk.network.proto.MessagesP;
import jsmith.nknsdk.network.proto.SigchainP;
import jsmith.nknsdk.utils.Crypto;
import jsmith.nknsdk.utils.EncodeUtils;
import jsmith.nknsdk.wallet.Wallet;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class ClientEnc {


    public static void signOutboundMessage(MessagesP.ClientMsg.Builder msg, ClientTunnel ct) {

        final SigchainP.SigChainElem sigChainElem = SigchainP.SigChainElem.newBuilder()
                .setNextPubkey(ct.nodePubkey)
                .build();

        final byte[] sigChainElemBA =
                EncodeUtils.encodeBytes(sigChainElem.getId())
                .concat(EncodeUtils.encodeBytes(sigChainElem.getNextPubkey()))
                .concat(EncodeUtils.encodeBool(sigChainElem.getMining()))
                        .toByteArray();

        final SigchainP.SigChain.Builder sigChain = SigchainP.SigChain.newBuilder()
                .setNonce(Crypto.nextRandomInt32())
                .setDataSize(msg.getPayload().size())
                .setSrcId(ByteString.copyFrom(Crypto.sha256(ct.identity.getFullIdentifier().getBytes(Charset.forName("UTF-8")))))
                .setSrcPubkey(ByteString.copyFrom(ct.identity.wallet.getPublicKey()));

        final ByteString bh = ct.currentSigChainBlockHash();
        if (bh != null) {
            sigChain.setBlockHash(bh);
        }

        for (String dest : msg.getDestsList()) {
            sigChain.setDestId(ByteString.copyFrom(Crypto.sha256(dest.getBytes(Charset.forName("UTF-8")))));
            sigChain.setDestPubkey(ByteString.copyFrom(Hex.decode(dest.substring(dest.lastIndexOf('.') + 1))));

            ByteString hex =
                    EncodeUtils.encodeUint32(sigChain.getNonce())
                    .concat(EncodeUtils.encodeUint32(sigChain.getDataSize()))
                    .concat(EncodeUtils.encodeBytes(sigChain.getBlockHash()))
                    .concat(EncodeUtils.encodeBytes(sigChain.getSrcId()))
                    .concat(EncodeUtils.encodeBytes(sigChain.getSrcPubkey()))
                    .concat(EncodeUtils.encodeBytes(sigChain.getDestId()))
                    .concat(EncodeUtils.encodeBytes(sigChain.getDestPubkey()));

            final byte[] hexHash = Crypto.sha256(hex.toByteArray());
            final byte[] toSign = new byte[hexHash.length + sigChainElemBA.length];
            System.arraycopy(hexHash, 0, toSign, 0, hexHash.length);
            System.arraycopy(sigChainElemBA, 0, toSign, hexHash.length, sigChainElemBA.length);

            msg.addSignatures(ByteString.copyFrom(ct.identity.wallet.sha256andSign(toSign)));
        }

        msg.setBlockHash(sigChain.getBlockHash());
        msg.setNonce(sigChain.getNonce());
    }


    public static ByteString generateNewReceipt(ByteString prevSignature, ClientTunnel ct) {
        final SigchainP.SigChainElem sigChainElem = SigchainP.SigChainElem.newBuilder().build();

        final byte[] sigChainElemBA =
                EncodeUtils.encodeBytes(sigChainElem.getId())
                        .concat(EncodeUtils.encodeBytes(sigChainElem.getNextPubkey()))
                        .concat(EncodeUtils.encodeBool(sigChainElem.getMining()))
                        .toByteArray();

        final byte[] hexHash = Crypto.sha256(prevSignature.toByteArray());
        final byte[] toSign = new byte[hexHash.length + sigChainElemBA.length];
        System.arraycopy(hexHash, 0, toSign, 0, hexHash.length);
        System.arraycopy(sigChainElemBA, 0, toSign, hexHash.length, sigChainElemBA.length);
        final ByteString signature = ByteString.copyFrom(ct.identity.wallet.sha256andSign(toSign));

        return MessagesP.ReceiptMsg.newBuilder()
                .setPrevSignature(prevSignature)
                .setSignature(signature)
                .build().toByteString();
    }



    public static ByteString encryptMessage(List<String> destinations, ByteString message, Wallet wallet) {
//        if (destinations.size() > 1) throw new NKNClientException("Encryption with multicast is not supported yet");
        final String dest = destinations.get(0);

        final byte[] nonce = TweetNaclFast.randombytes(24);
        final byte[] sharedKey = wallet.getSharedKey(dest);
        final byte[] msg = message.toByteArray();
        final byte[] m = new byte[32 + message.size()];
        final byte[] encrypted = new byte[m.length];
        System.arraycopy(msg, 0, m, 32, msg.length);

        TweetNaclFast.crypto_box_afternm(encrypted, m, encrypted.length, nonce, sharedKey);

        final MessagesP.EncryptedMessage encMsg = MessagesP.EncryptedMessage.newBuilder()
                .setNonce(ByteString.copyFrom(nonce))
                .setPayload(ByteString.copyFrom(encrypted))
                .setEncrypted(true)

                .build();

        return encMsg.toByteString();
    }

    public static ByteString decryptMessage(String from, MessagesP.EncryptedMessage enc, Wallet wallet) throws NKNClientException {
        if (enc.getEncrypted()) {

            final byte[] sharedKey = wallet.getSharedKey(from);
            final byte[] ciphertext = enc.getPayload().toByteArray();
            final byte[] plaintext = new byte[ciphertext.length];

            if (TweetNaclFast.crypto_box_open_afternm(plaintext, ciphertext, ciphertext.length, enc.getNonce().toByteArray(), sharedKey) != 0) {
                throw new NKNClientException("Message decryption failed");
            }
            return ByteString.copyFrom(plaintext, 32, plaintext.length - 32);

        } else {
            return enc.getPayload();
        }
    }


//    Key.prototype.getOrComputeSharedKey = function (otherPubkey) {
//        if (!this.sharedKeyCache[otherPubkey]) {
//            let otherCurvePubkey = ed2curve.convertPublicKey(otherPubkey);
//            this.sharedKeyCache[otherPubkey] = nacl.box.before(otherCurvePubkey, this.curveSecretKey);
//        }
//        return this.sharedKeyCache[otherPubkey];
//    }
//
//    public static byte[] encryptEd(byte[] data, byte[] destPk, byte[] nonce) {
//
//        let sharedKey = this.getOrComputeSharedKey(destPubkey);
//        let nonce = options.nonce || nacl.randomBytes(nacl.box.nonceLength);
//        return {
//                message: nacl.box.after(message, nonce, sharedKey),
//                nonce: nonce,
//        };
//    }
//
//    Key.prototype.decrypt = function (encryptedMessage, nonce, srcPubkey, options = {}) {
//        let sharedKey = this.getOrComputeSharedKey(srcPubkey);
//        return nacl.box.open.after(encryptedMessage, nonce, sharedKey);
//    }
//
}
