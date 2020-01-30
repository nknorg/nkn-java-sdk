package jsmith.nknsdk.client;

import com.google.protobuf.ByteString;
import jsmith.nknsdk.network.ClientMessageWorkers;
import jsmith.nknsdk.network.ClientTunnel;
import jsmith.nknsdk.network.Session;
import jsmith.nknsdk.network.SessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 */
public class NKNClient {

    private static final Logger LOG = LoggerFactory.getLogger(NKNClient.class);

    private final ClientTunnel clientTunnel;
    private final ClientMessageWorkers clientMessageWorkers;
    private final SimpleMessages simpleMessages;
    private final Identity identity;
    private final SessionHandler sessionHandler;

    public NKNClient(Identity identity) {
        this.identity = identity;
        this.clientTunnel = new ClientTunnel(identity, this);
        this.clientMessageWorkers = clientTunnel.getAssociatedCM();
        this.simpleMessages = new SimpleMessages(this.clientMessageWorkers);
        this.sessionHandler = new SessionHandler(clientTunnel);
    }

    public NKNClient start() throws NKNClientException {
        clientTunnel.startClient();
        return this;
    }

    public void close() {
        clientTunnel.close();
    }

    public SimpleMessages simpleMessagesProtocol() {
        return simpleMessages;
    }

    public SessionHandler sessionProtocol() {
        return sessionHandler;
    }



    private EncryptionLevel encryptionLevel = EncryptionLevel.CONVERT_MULTICAST_TO_UNICAST_AND_ENCRYPT;
    public NKNClient setEncryptionLevel(EncryptionLevel level) {
        this.encryptionLevel = level;
        return this;
    }
    public EncryptionLevel getEncryptionLevel() {
        return encryptionLevel;
    }

    private PeerEncryptionRequirement encryptionRequirement = PeerEncryptionRequirement.ON_NON_ENCRYPTED_MESSAGE___ALLOW_ALL_DROP_NONE;
    public NKNClient setPeerEncryptionRequirement(PeerEncryptionRequirement requirement) {
        this.encryptionRequirement = requirement;
        return this;
    }
    public PeerEncryptionRequirement getPeerEncryptionRequirement() {
        return encryptionRequirement;
    }



    public ByteString getCurrentSigChainBlockHash() {
        return clientTunnel.currentSigChainBlockHash();
    }



    public enum EncryptionLevel {

        DO_NOT_ENCRYPT,
        ENCRYPT_ONLY_UNICAST,
        CONVERT_MULTICAST_TO_UNICAST_AND_ENCRYPT,
        ENCRYPT_UNICAST_AND_MULTICAST

    }

    public enum PeerEncryptionRequirement {

        ON_NON_ENCRYPTED_MESSAGE___ALLOW_NONE_DROP_ALL,
        ON_NON_ENCRYPTED_MESSAGE___ALLOW_ACK_DROP_OTHER,
        ON_NON_ENCRYPTED_MESSAGE___ALLOW_ALL_DROP_NONE

    }

}
