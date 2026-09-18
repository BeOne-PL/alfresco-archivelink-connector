package pl.beone.archivelink.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.stereotype.Component;
import pl.beone.archivelink.exception.MissingNodeException;
import pl.beone.archivelink.exception.SAPSecurityException;
import pl.beone.archivelink.model.SapCertModel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Optional;

/**
 * Utility service for loading SAP public key from a file
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SapPublicKeyUtil {

    private final ContentRepositoryService contRepService;
    private final ArchiveLinkFileUtil fileUtil;
    @Qualifier("NodeService") private final NodeService nodeService;
    @Qualifier("ContentService") private final ContentService contentService;

    public Optional<PublicKey> loadPublicKey(byte[] publicKeyBytes) throws SAPSecurityException {

        try {
            return Optional.ofNullable(parsePublicKey(publicKeyBytes));
        } catch (Exception e) {
            log.error("Loading public key failed: ", e);
            return Optional.empty();
        }
    }

    /**
     * Loads SAP public key depending on a type it's stored as
     *
     * @return Public key
     * @throws SAPSecurityException When the public key is not found or cannot be loaded
     */
    public Optional<PublicKey> loadPublicKey(String contRep) throws SAPSecurityException {
        try (InputStream publicKeyStream = readPublicKey(contRep)) {
            return Optional.ofNullable(parsePublicKey(publicKeyStream.readAllBytes()));
        } catch (Exception e) {
            log.error("Loading public key failed for content repository: {}", contRep, e);
            throw new SAPSecurityException("Could not load public key from: " + contRep);
        }
    }

    private PublicKey parsePublicKey(byte[] publicKeyBytes) throws IOException, CertificateException {

        if (publicKeyBytes.length <= 0) {
            return null;
        }

        String headerStr = new String(publicKeyBytes, StandardCharsets.UTF_8);

        if (headerStr.contains("-----BEGIN CERTIFICATE-----")) {
            log.debug("Detected X.509 certificate format");
            return loadFromCertificate(publicKeyBytes);
        } else if (headerStr.contains("-----BEGIN PKCS7-----")) {
            log.debug("Detected PKCS#7 certificate format");
            return loadFromPKCS7(publicKeyBytes);
        } else if (headerStr.contains("-----BEGIN PUBLIC KEY-----")) {
            log.debug("Detected PEM public key format");
            return loadFromPEM(publicKeyBytes);
        } else {
            log.debug("Assuming DER format (could be PKCS#7 DER or raw key)");

            var pkcs7Key = loadFromPKCS7(publicKeyBytes);
            if (pkcs7Key != null) {
                return pkcs7Key;
            }

            return loadFromDER(publicKeyBytes);
        }
    }

    /**
     * Returns a certificate folder inside a SAP content repository folder. If it cannot find one, creates it.
     * If content repository folder doesn't exist, creates it.
     *
     * @param contRep Content repository name
     * @return Reference to a certificate folder
     * @throws MissingNodeException When the company home folder is not found
     */
    public NodeRef getOrCreateCertificateFolder(String contRep) throws MissingNodeException {
        var contRepFolder = contRepService.getOrCreateContentRepositoryFolder(contRep);

        var certificateFolder = nodeService.getChildByName(contRepFolder, ContentModel.ASSOC_CONTAINS, "certFolder");
        if (certificateFolder == null) {
            var props = new HashMap<QName, Serializable>();
            props.put(ContentModel.PROP_NAME, "certFolder");
            props.put(ContentModel.PROP_TITLE, "Certificate Folder");

            certificateFolder = nodeService.createNode(
                    contRepFolder,
                    ContentModel.ASSOC_CONTAINS,
                    QName.createQName(ContentModel.USER_MODEL_URI, "certFolder"),
                    ContentModel.TYPE_FOLDER,
                    props
            ).getChildRef();
        }

        return certificateFolder;
    }

    /**
     * Creates a public key file at a specified folder and writes the content to it, or overwrites it if it already exists at the location.
     *
     * @param publicKeyFolder Folder where public key is put
     * @param content         Public key content to write
     */
    public void createPublicKey(NodeRef publicKeyFolder, InputStream content) {
        var publicKey = getPublicKey(publicKeyFolder);

        if (publicKey.isPresent()) {
            var nodeRef = publicKey.get();
            if (isCertAccepted(nodeRef)) {
                throw new WebScriptException(Status.STATUS_FORBIDDEN, "Forbidden certificate action");
            }

            fileUtil.writeContentToNode(MimetypeMap.MIMETYPE_BINARY, nodeRef, content);
            addIsAcceptedAspect(nodeRef);
            return;
        }

        var props = new HashMap<QName, Serializable>();
        props.put(ContentModel.PROP_NAME, "cert");
        props.put(ContentModel.PROP_TITLE, "Content Repository Certificate");

        var newPublicKey = nodeService.createNode(
                publicKeyFolder,
                ContentModel.ASSOC_CONTAINS,
                QName.createQName(ContentModel.USER_MODEL_URI, "cert"),
                ContentModel.TYPE_CONTENT,
                props
        ).getChildRef();
        addIsAcceptedAspect(newPublicKey);

        fileUtil.writeContentToNode(MimetypeMap.MIMETYPE_BINARY, newPublicKey, content);
    }

    private void addIsAcceptedAspect(NodeRef publicKey) {
        var aspectProps = new HashMap<QName, Serializable>();
        aspectProps.put(SapCertModel.PROP_IS_ACCEPTED, false);
        nodeService.addAspect(publicKey, SapCertModel.ASPECT_ACCEPTED, aspectProps);
    }

    private boolean isCertAccepted(NodeRef publicKey) {
        Boolean isKeyAccepted = (Boolean) nodeService.getProperty(publicKey, SapCertModel.PROP_IS_ACCEPTED);
        return Boolean.TRUE.equals(isKeyAccepted);
    }

    /**
     * Reads public key from a content repository
     *
     * @param contRep Content repository name
     * @return InputStream containing public key
     * @throws SAPSecurityException When the certificate folder or public key are not found
     */
    private InputStream readPublicKey(String contRep) {
        var certificateFolder = getCertificateFolder(contRep)
                .orElseThrow(SAPSecurityException::missingPublicKey);

        var publicKeyNode = getPublicKey(certificateFolder)
                .orElseThrow(SAPSecurityException::missingPublicKey);

        Boolean isKeyAccepted = (Boolean) nodeService.getProperty(publicKeyNode, SapCertModel.PROP_IS_ACCEPTED);
        if (!Boolean.TRUE.equals(isKeyAccepted)) {
            throw SAPSecurityException.publicKeyNotAccepted();
        }

        var contentReader = contentService.getReader(publicKeyNode, ContentModel.PROP_CONTENT);

        if (contentReader == null || !contentReader.exists()) {
            throw SAPSecurityException.missingPublicKey();
        }

        try {
            var inputStream = contentReader.getContentInputStream();
            return inputStream.markSupported() ? inputStream : new BufferedInputStream(inputStream);
        } catch (Exception e) {
            log.error("Failed to read public key file content from content repository: {}", contRep, e);
            throw SAPSecurityException.missingPublicKey();
        }
    }

    /**
     * Returns a certificate folder inside a SAP content repository folder. If content repository folder doesn't exist, creates it.
     *
     * @param contRep Content repository name
     * @return Reference to a certificate folder
     */
    private Optional<NodeRef> getCertificateFolder(String contRep) {
        var contRepFolder = contRepService.getOrCreateContentRepositoryFolder(contRep);

        return Optional.ofNullable(nodeService.getChildByName(contRepFolder, ContentModel.ASSOC_CONTAINS, "certFolder"));
    }

    /**
     * Gets a public key from a specified folder
     *
     * @param publicKeyFolder Folder of the public key
     * @return Optional of a public key (empty if not found)
     */
    public Optional<NodeRef> getPublicKey(NodeRef publicKeyFolder) {
        var certNode = nodeService.getChildByName(publicKeyFolder, ContentModel.ASSOC_CONTAINS, "cert");
        return Optional.ofNullable(certNode);
    }

    /**
     * Loads public key from a PEM file
     *
     * @param certBytes Certificate InputStream
     * @return PublicKey
     * @throws IOException When the PEM file cannot be read
     */
    private PublicKey loadFromPEM(byte[] certBytes) throws IOException {
        String pemContent = new String(certBytes, StandardCharsets.UTF_8);

        String cleanedPem = pemContent
                .replaceAll("-----BEGIN.*-----", "")
                .replaceAll("-----END.*-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.decodeBase64(cleanedPem);

        PublicKey publicKey = tryLoadWithAlgorithm(keyBytes, "RSA");
        if (publicKey == null) publicKey = tryLoadWithAlgorithm(keyBytes, "DSA");
        if (publicKey == null) publicKey = tryLoadWithAlgorithm(keyBytes, "EC");

        if (publicKey != null) {
            log.debug("Successfully loaded public key from PEM file. Algorithm: {}", publicKey.getAlgorithm());
        }

        return publicKey;
    }

    /**
     * Loads public key from a DER file
     *
     * @param certBytes Certificate InputStream
     * @return Public key
     * @throws IOException When the DER file cannot be read
     */
    private PublicKey loadFromDER(byte[] certBytes) throws IOException {

        PublicKey publicKey = tryLoadWithAlgorithm(certBytes, "RSA");
        if (publicKey == null) publicKey = tryLoadWithAlgorithm(certBytes, "DSA");
        if (publicKey == null) publicKey = tryLoadWithAlgorithm(certBytes, "EC");

        if (publicKey != null) {
            log.debug("Successfully loaded public key from DER file. Algorithm: {}", publicKey.getAlgorithm());
        }

        return publicKey;
    }

    /**
     * Loads public key from an X.509 certificate
     *
     * @param certBytes Certificate InputStream
     * @return Public key
     * @throws CertificateException When the certificate cannot be parsed
     */
    private PublicKey loadFromCertificate(byte[] certBytes) throws CertificateException {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));

        PublicKey publicKey = certificate.getPublicKey();
        log.debug("Successfully loaded public key from X.509 certificate. Algorithm: {}", publicKey.getAlgorithm());

        return publicKey;
    }

    /**
     * Loads public key from a PKCS#7 certificate chain (PEM or DER).
     * It extracts the public key from the first certificate found.
     *
     * @param certBytes Certificate InputStream.
     * @return Public key from the first certificate in the chain
     * @throws CertificateException When loading certificate fails
     */
    private PublicKey loadFromPKCS7(byte[] certBytes) throws CertificateException {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

        var certificates = certFactory.generateCertificates(new ByteArrayInputStream(certBytes));
        if (certificates.isEmpty()) {
            log.error("PKCS#7 certificate stream contained no certificates.");
            return null;
        }

        X509Certificate certificate = (X509Certificate) certificates.iterator().next();
        PublicKey publicKey = certificate.getPublicKey();

        log.debug("Successfully loaded public key from PKCS#7 certificate. Algorithm: {}", publicKey.getAlgorithm());
        return publicKey;
    }

    /**
     * Tries to load a public key with a specific algorithm
     *
     * @param keyBytes  Public key bytes
     * @param algorithm Public key algorithm
     * @return Public key or null if something went wrong
     */
    private PublicKey tryLoadWithAlgorithm(byte[] keyBytes, String algorithm) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            return keyFactory.generatePublic(keySpec);

        } catch (Exception e) {
            log.debug("Failed to load key with algorithm {}: {}", algorithm, e.getMessage());
            return null;
        }
    }

}
