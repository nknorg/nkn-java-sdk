package jsmith.nknsdk.network;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.network.proto.MessagesP;
import jsmith.nknsdk.network.proto.SigchainP;
import jsmith.nknsdk.utils.Crypto;
import jsmith.nknsdk.utils.EncodeUtils;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.Charset;

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

}
