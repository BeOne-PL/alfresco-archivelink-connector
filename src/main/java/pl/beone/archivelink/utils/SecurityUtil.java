package pl.beone.archivelink.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.stereotype.Component;
import pl.beone.archivelink.exception.DocumentNotFoundException;
import pl.beone.archivelink.exception.MissingMandatoryParameterException;
import pl.beone.archivelink.exception.SAPSecurityException;
import pl.beone.archivelink.model.ArchivelinkFunction;
import pl.beone.archivelink.model.SapDocumentModel;

import java.io.Serializable;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;


@Slf4j
@RequiredArgsConstructor
@Component
public class SecurityUtil {

    private static final String DEFAULT_DOC_PROT = "crud";

    private static final List<String> S_SIGNED_PARAMS = Arrays.asList(
            "accessMode", "authId", "expiration"
    );

    @Qualifier("NodeService") private final NodeService nodeService;
    private final SapPublicKeyUtil sapPublicKeyLoader;
    private final ContentRepositoryService contRepService;

    /**
     * Checks mandatory request params for a function and checks if the request contains them all
     *
     * @param request             WebScript request
     * @param archivelinkFunction Function extracted from the query string
     * @throws MissingMandatoryParameterException When the request does not contain all mandatory parameters
     */
    public void validateMandatoryUrlParameters(
            WebScriptRequest request,
            ArchivelinkFunction archivelinkFunction
    ) throws MissingMandatoryParameterException {
        for (String mandatoryParameter : archivelinkFunction.getMandatoryParameters()) {
            if (request.getParameter(mandatoryParameter) == null) {
                throw new MissingMandatoryParameterException(mandatoryParameter);
            }
        }
    }

    /**
     * Validates SAP ArchiveLink security of accessing a document
     *
     * @param request             WebScript request
     * @param archivelinkFunction Archivelink function
     * @throws SAPSecurityException When the security check fails
     */
    public void validateRequestSecKey(WebScriptRequest request, ArchivelinkFunction archivelinkFunction) throws SAPSecurityException {
        String docProt = getDocProt(request);
        if (docProt == null || docProt.isBlank() || !docProt.contains(archivelinkFunction.getAccessMode())) {
            log.debug("No security check required for docProt: {}", docProt);
            return;
        }

        String accessMode = request.getParameter("accessMode");
        validateAccessMode(accessMode, archivelinkFunction.getAccessMode());

        String expiration = request.getParameter("expiration");
        validateExpiration(expiration);

        String authId = request.getParameter("authId");
        validateAuthId(authId);

        String secKey = request.getParameter("secKey");
        validateSecKey(secKey);

        String contRep = request.getParameter("contRep");
        validateContRep(contRep);

        PublicKey sapPublicKey = sapPublicKeyLoader.loadPublicKey(contRep)
                .orElseThrow(SAPSecurityException::missingPublicKey);

        String signedUrl = getSignedUrl(request, archivelinkFunction).replace("=", "%3D");
        verifySignature(secKey, signedUrl, sapPublicKey);

        log.debug("SAP ArchiveLink security validation successful");
    }

    /**
     * Gets signed URL from request
     *
     * @param request             WebScript request
     * @param archivelinkFunction Archivelink function
     * @return Signed URL
     * @throws SAPSecurityException When the query parameter cannot be decoded
     */
    private String getSignedUrl(WebScriptRequest request, ArchivelinkFunction archivelinkFunction) throws SAPSecurityException {
        StringBuilder message = new StringBuilder();

        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            // Skip everything up to the first '&': it belongs to the function name (e.g. "create&rest-of-params"), which isn't part of the signed message
            int firstAmpersand = queryString.indexOf('&');
            if (firstAmpersand != -1) {
                String parametersOnly = queryString.substring(firstAmpersand + 1);

                String[] pairs = parametersOnly.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    try {
                        String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);

                        if (S_SIGNED_PARAMS.contains(key) || archivelinkFunction.getSignedParameters().contains(key)) {
                            message.append(URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
                        }
                    } catch (Exception e) {
                        log.error("Failed to decode query parameter: {}", pair, e);
                        throw SAPSecurityException.invalidSignature();
                    }
                }
            }
        }

        return message.toString();
    }

    /**
     * Checks if secKey is present
     *
     * @param secKey Security key
     * @throws SAPSecurityException When the secKey is missing
     */
    private void validateSecKey(String secKey) throws SAPSecurityException {
        if (StringUtils.isBlank(secKey)) {
            throw SAPSecurityException.missingSecKey();
        }
    }

    /**
     * Checks if client authId is present.
     * <p>
     * Note: this only checks presence. The signature itself is verified against the public key registered
     * for the request's {@code contRep}, so authId is not currently cross-checked against a specific
     * certificate/client. Revisit if per-client certificate binding is required.
     *
     * @param authId Client authID
     * @throws SAPSecurityException When the authId is missing
     */
    private void validateAuthId(String authId) throws SAPSecurityException {
        if (StringUtils.isBlank(authId)) {
            throw SAPSecurityException.missingAuthId();
        }
    }

    /**
     * Checks if content repository is present
     *
     * @param contRep Content repository name
     * @throws SAPSecurityException When the contRep is mising
     */
    private void validateContRep(String contRep) {
        if (StringUtils.isBlank(contRep)) {
            throw SAPSecurityException.missingContentRepository();
        }
    }

    /**
     * Extracts document's degree of protection
     *
     * @param request WebScript request
     * @return Document's degree of protection or default value (if doc not found or docProt is null)
     */
    private String getDocProt(WebScriptRequest request) {
        String docId = request.getParameter("docId");
        String contRep = request.getParameter("contRep");

        try {
            NodeRef document = contRepService.getDocument(docId, contRep);
            Serializable docProtDoc = nodeService.getProperty(document, SapDocumentModel.PROP_SAP_DOC_PROT);
            return docProtDoc != null ? docProtDoc.toString() : DEFAULT_DOC_PROT;
        } catch (DocumentNotFoundException e) {
            return DEFAULT_DOC_PROT;
        }
    }

    /**
     * Verifies the signature of a signed url
     *
     * @param secKey    Security key
     * @param signedUrl Signed URL
     * @param publicKey Public Key
     * @throws SAPSecurityException When the signature verification fails
     */
    private void verifySignature(String secKey, String signedUrl, PublicKey publicKey) throws SAPSecurityException {
        try {
            byte[] signatureBytes = Base64.decodeBase64(secKey);

            if (!verifyDSASignature(signedUrl, signatureBytes, publicKey)) {
                throw SAPSecurityException.invalidSignature();
            }

        } catch (SAPSecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while validating secKey", e);
            throw SAPSecurityException.signatureVerificationFailed();
        }
    }

    /**
     * Verifies DSA signature of a message
     *
     * @param message        Checked message
     * @param signatureBytes Signature bytes
     * @param publicKey      Public key
     * @return True if the signature is valid, false otherwise
     */
    private static boolean verifyDSASignature(String message, byte[] signatureBytes, PublicKey publicKey) {
        try {
            var content = new CMSProcessableByteArray(message.getBytes(StandardCharsets.UTF_8));

            var signedData = new CMSSignedData(content, signatureBytes);

            var signers = signedData.getSignerInfos();

            var signerInformationVerifier = new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(publicKey);
            for (var signer : signers.getSigners()) {
                if (signer.verify(signerInformationVerifier)) {
                    log.debug("SAP Signature Verified Successfully for signer: {}", signer.getSID());
                    return true;
                }
            }

            log.error("No valid signer found in secKey.");
            return false;
        } catch (Exception e) {
            log.error("Error while verifying DSA signature", e);
            return false;
        }
    }

    /**
     * Checks if key expiration date is provided, it's of correct format and if the key isn't past its expiration date
     *
     * @param expiration Expiration date string
     * @throws SAPSecurityException When the expiration isn't provided, it's of incorrect format or the date has passed
     */
    private void validateExpiration(String expiration) throws SAPSecurityException {
        if (StringUtils.isBlank(expiration)) {
            throw SAPSecurityException.missingExpiration();
        }

        try {
            if (!Pattern.matches("\\d{14}", expiration)) {
                throw SAPSecurityException.invalidExpiration(expiration);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            Date expirationDate = sdf.parse(expiration);
            Date now = new Date();

            if (expirationDate.before(now)) {
                throw SAPSecurityException.urlExpired(expiration);
            }

        } catch (SAPSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw SAPSecurityException.invalidExpiration(expiration);
        }
    }

    /**
     * Validates if provided access rights are sufficient for a given doc
     *
     * @param providedAccess Provided access
     * @param requiredAccess Required access
     * @throws SAPSecurityException When provided access is not sufficient
     */
    private void validateAccessMode(String providedAccess, String requiredAccess) throws SAPSecurityException {
        if (!hasRequiredAccess(providedAccess, requiredAccess)) {
            throw SAPSecurityException.insufficientAccess(requiredAccess, providedAccess);
        }
    }

    private boolean hasRequiredAccess(String providedAccess, String requiredAccess) {
        if (StringUtils.isBlank(providedAccess) || StringUtils.isBlank(requiredAccess)) {
            return false;
        }

        for (char required : requiredAccess.toCharArray()) {
            if (providedAccess.indexOf(required) == -1) {
                return false;
            }
        }

        return true;
    }

}
