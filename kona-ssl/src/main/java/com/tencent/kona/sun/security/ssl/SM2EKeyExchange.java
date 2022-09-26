package com.tencent.kona.sun.security.ssl;

import com.tencent.kona.crypto.CryptoInsts;
import com.tencent.kona.crypto.spec.SM2KeyAgreementParamSpec;
import com.tencent.kona.crypto.util.Constants;
import com.tencent.kona.ssl.SSLUtils;
import com.tencent.kona.sun.security.util.ECUtil;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.security.AlgorithmConstraints;
import java.security.CryptoPrimitive;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.EnumSet;

/**
 * TLCP ephemeral SM2 key exchange.
 */
public class SM2EKeyExchange {

    static final SSLPossessionGenerator sm2ePoGenerator
            = new SM2EPossessionGenerator();
    static final SSLKeyAgreementGenerator sm2eKAGenerator
            = new SM2EKAGenerator();

    static final class SM2ECredentials implements NamedGroupCredentials {

        final ECPublicKey ephemeralPublicKey;
        final NamedGroup namedGroup;

        SM2ECredentials(ECPublicKey ephemeralPublicKey, NamedGroup namedGroup) {
            this.ephemeralPublicKey = ephemeralPublicKey;
            this.namedGroup = namedGroup;
        }

        @Override
        public PublicKey getPublicKey() {
            return ephemeralPublicKey;
        }

        @Override
        public NamedGroup getNamedGroup() {
            return namedGroup;
        }

        static SM2ECredentials valueOf(NamedGroup namedGroup,
            byte[] encodedPoint) throws IOException, GeneralSecurityException {

            if (namedGroup != NamedGroup.CURVESM2) {
                throw new RuntimeException(
                        "Credentials decoding: Not named group curveSM2");
            }

            if (encodedPoint == null || encodedPoint.length == 0) {
                return null;
            }

            ECParameterSpec parameters =
                    (ECParameterSpec)namedGroup.keAlgParamSpec;
            ECPoint point = ECUtil.decodePoint(
                    encodedPoint, parameters.getCurve());
            KeyFactory factory = CryptoInsts.getKeyFactory("SM2");
            ECPublicKey publicKey = (ECPublicKey)factory.generatePublic(
                    new ECPublicKeySpec(point, parameters));
            return new SM2ECredentials(publicKey, namedGroup);
        }
    }

    static final class SM2EPossession implements NamedGroupPossession {

        final ECPrivateKey ephemeralPrivateKey;
        final ECPublicKey ephemeralPublicKey;

        final ECPrivateKey popEncPrivateKey;
        final ECPublicKey popEncPublicKey;
        final NamedGroup namedGroup;

        SM2EPossession(TLCPAuthentication.TLCPPossession tlcpPossession,
                       NamedGroup namedGroup, SecureRandom random) {
            try {
                KeyPairGenerator kpg
                        = SSLUtils.getECKeyPairGenerator(namedGroup.name);
                kpg.initialize(namedGroup.keAlgParamSpec, null);
                KeyPair kp = kpg.generateKeyPair();
                ephemeralPrivateKey = (ECPrivateKey) kp.getPrivate();
                ephemeralPublicKey = (ECPublicKey)kp.getPublic();
            } catch (GeneralSecurityException e) {
                throw new RuntimeException("Could not generate SM2 keypair", e);
            }

            popEncPrivateKey = (ECPrivateKey) tlcpPossession.popEncPrivateKey;
            popEncPublicKey = (ECPublicKey) tlcpPossession.popEncPublicKey;
            this.namedGroup = namedGroup;
        }

        @Override
        public byte[] encode() {
            byte[] encodedPoint = ECUtil.encodePoint(
                    ephemeralPublicKey.getW(),
                    ephemeralPublicKey.getParams().getCurve());
            return encodedPoint;
        }

        // called by ClientHandshaker with either the server's static or
        // ephemeral public key
        SecretKey getAgreedSecret(
                ECPublicKey peerEphemeralPublicKey, boolean isInitiator)
                throws SSLHandshakeException {
            try {
                SM2KeyAgreementParamSpec params = new SM2KeyAgreementParamSpec(
                        Constants.defaultId(),
                        popEncPrivateKey,
                        popEncPublicKey,
                        Constants.defaultId(),
                        peerEphemeralPublicKey,
                        isInitiator,
                        32);

                KeyAgreement ka = CryptoInsts.getKeyAgreement("SM2");
                ka.init(ephemeralPrivateKey, params);
                ka.doPhase(peerEphemeralPublicKey, true);
                return ka.generateSecret("TlsPremasterSecret");
            } catch (GeneralSecurityException e) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                        "Could not generate secret").initCause(e);
            }
        }

        // called by ServerHandshaker
        SecretKey getAgreedSecret(
                byte[] peerEphemeralEncodedPoint, boolean initiator)
                throws SSLHandshakeException {
            try {
                ECParameterSpec params = ephemeralPublicKey.getParams();
                ECPoint point = ECUtil.decodePoint(
                        peerEphemeralEncodedPoint, params.getCurve());
                KeyFactory kf = CryptoInsts.getKeyFactory("SM2");
                ECPublicKeySpec spec = new ECPublicKeySpec(point, params);
                ECPublicKey peerPublicKey = (ECPublicKey) kf.generatePublic(spec);
                return getAgreedSecret(peerPublicKey, initiator);
            } catch (GeneralSecurityException | IOException e) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                        "Could not generate secret").initCause(e);
            }
        }

        // Check constraints of the specified EC public key.
        void checkConstraints(AlgorithmConstraints constraints,
                              byte[] encodedPoint) throws SSLHandshakeException {
            try {

                ECParameterSpec params = ephemeralPublicKey.getParams();
                ECPoint point =
                        ECUtil.decodePoint(encodedPoint, params.getCurve());
                ECPublicKeySpec spec = new ECPublicKeySpec(point, params);

                KeyFactory kf = CryptoInsts.getKeyFactory("SM2");
                ECPublicKey pubKey = (ECPublicKey)kf.generatePublic(spec);

                // check constraints of ECPublicKey
                if (!constraints.permits(
                        EnumSet.of(CryptoPrimitive.KEY_AGREEMENT), pubKey)) {
                    throw new SSLHandshakeException(
                        "ECPublicKey does not comply to algorithm constraints");
                }
            } catch (GeneralSecurityException | IOException e) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                        "Could not generate ECPublicKey").initCause(e);
            }
        }

        @Override
        public PublicKey getPublicKey() {
            return ephemeralPublicKey;
        }

        @Override
        public NamedGroup getNamedGroup() {
            return namedGroup;
        }

        @Override
        public PrivateKey getPrivateKey() {
            return ephemeralPrivateKey;
        }
    }

    private static final class SM2EPossessionGenerator
            implements SSLPossessionGenerator {

        // Prevent instantiation of this class.
        private SM2EPossessionGenerator() {
            // blank
        }

        @Override
        public SSLPossession createPossession(HandshakeContext context) {

            NamedGroup preferableNamedGroup;

            // Find most preferred EC or XEC groups
            if ((context.clientRequestedNamedGroups != null) &&
                    (!context.clientRequestedNamedGroups.isEmpty())) {
                preferableNamedGroup = SupportedGroupsExtension.SupportedGroups.getPreferredGroup(
                        context.negotiatedProtocol,
                        context.algorithmConstraints,
                        new NamedGroup.NamedGroupSpec[] {
                            NamedGroup.NamedGroupSpec.NAMED_GROUP_ECDHE },
                        context.clientRequestedNamedGroups);
            } else {
                preferableNamedGroup = SupportedGroupsExtension.SupportedGroups.getPreferredGroup(
                        context.negotiatedProtocol,
                        context.algorithmConstraints,
                        new NamedGroup.NamedGroupSpec[] {
                            NamedGroup.NamedGroupSpec.NAMED_GROUP_ECDHE });
            }

            ServerHandshakeContext shc = (ServerHandshakeContext) context;
            TLCPAuthentication.TLCPPossession tlcpPossession = null;
            if (shc.interimAuthn instanceof TLCPAuthentication.TLCPPossession) {
                tlcpPossession = ((TLCPAuthentication.TLCPPossession) shc.interimAuthn);
            }
            if (preferableNamedGroup == NamedGroup.CURVESM2) {
                return new SM2EPossession(tlcpPossession,
                        preferableNamedGroup, context.sslContext.getSecureRandom());
            }

            // no match found, cannot use this cipher suite.
            return null;
        }
    }

    private static final class SM2EKAGenerator
            implements SSLKeyAgreementGenerator {

        // Prevent instantiation of this class.
        private SM2EKAGenerator() {
            // blank
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
                HandshakeContext context) throws IOException {
            SM2EPossession sm2ePossession = null;
            SM2ECredentials sm2eCredentials = null;
            for (SSLPossession poss : context.handshakePossessions) {
                if (!(poss instanceof SM2EPossession)) {
                    continue;
                }

                NamedGroup ng = ((SM2EPossession)poss).namedGroup;
                for (SSLCredentials cred : context.handshakeCredentials) {
                    if (!(cred instanceof SM2ECredentials)) {
                        continue;
                    }
                    if (ng.equals(((SM2ECredentials)cred).namedGroup)) {
                        sm2eCredentials = (SM2ECredentials)cred;
                        break;
                    }
                }

                if (sm2eCredentials != null) {
                    sm2ePossession = (SM2EPossession)poss;
                    break;
                }
            }

            if (sm2ePossession == null || sm2eCredentials == null) {
                throw context.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                        "No sufficient SM2 key agreement parameters negotiated");
            }

            return new SM2KAKeyDerivation("SM2", context,
                    sm2ePossession.ephemeralPrivateKey,
                    sm2eCredentials.ephemeralPublicKey);
        }
    }
}