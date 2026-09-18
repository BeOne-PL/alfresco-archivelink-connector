package pl.beone.archivelink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.stereotype.Service;
import pl.beone.archivelink.utils.SapPublicKeyUtil;

import java.io.ByteArrayInputStream;

/**
 * Handles SAP putCert function.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d0077df99aa1d6ee10000000a42189c.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkPutCertService implements ArchiveLinkService {

    private final SapPublicKeyUtil publicKeyUtil;

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws Exception {
        String contRep = request.getParameter("contRep");

        log.info("contRep={}", contRep);

        var certificateContent = request.getContent().getInputStream();
        byte[] certBytes = certificateContent.readAllBytes();
        if (publicKeyUtil.loadPublicKey(certBytes).isEmpty()) {
            log.error("Rejecting putCert for {}: Invalid certificate format", contRep);
            throw new WebScriptException(Status.STATUS_NOT_ACCEPTABLE, "Certificate not recognized");
        }

        var targetFolder = publicKeyUtil.getOrCreateCertificateFolder(contRep);

        publicKeyUtil.createPublicKey(targetFolder, new ByteArrayInputStream(certBytes));

        log.info("Put certificate in {}", contRep);

        response.setStatus(Status.STATUS_OK);
    }

}
