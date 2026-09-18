package pl.beone.archivelink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.stereotype.Service;
import pl.beone.archivelink.utils.ArchiveLinkFileUtil;
import pl.beone.archivelink.utils.ContentRepositoryService;

import java.io.InputStream;

/**
 * Handles SAP create function, HTTP PUT variant.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d0578e0eaa85c4be10000000a42189e.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkCreatePutService implements ArchiveLinkService {

    private final ContentRepositoryService contRepService;
    private final ArchiveLinkFileUtil fileUtil;
//    private final SynchronizationService synchronizationService;

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws Exception {
        String docId = request.getParameter("docId");
        String contRep = request.getParameter("contRep");
        String compId = request.getParameter("compId");
        String docProt = request.getParameter("docProt");
        String pVersion = request.getParameter("pVersion");
        String contentType = request.getContentType();

        log.info("{}/{}/{}", contRep, docId, compId);

        contRepService.validateDuplicate(docId, contRep);

        InputStream contentStream = request.getContent().getInputStream();
        String contentLengthHeader = request.getHeader("Content-Length");

        validateContent(contentStream, contentLengthHeader);

        String mimeType = fileUtil.determineMimeType(contentType);

        var contentRepository = contRepService.getOrCreateContentRepositoryFolder(contRep);
        var targetFolder = contRepService.getOrCreateFolderStructure(contentRepository);

        var documentNode = contRepService.createDocument(targetFolder, docId, contRep, docProt, pVersion);
        contRepService.createDocumentComponent(documentNode, compId, mimeType, contentStream, contRep, docId, pVersion);

        response.setStatus(Status.STATUS_CREATED);

        log.info("doc created: docId={}, compId={}", docId, compId);

//        synchronizationService.waitForEndOfUpdate();
    }

    private void validateContent(InputStream contentStream, String contentLengthHeader) {
        if (contentStream == null) {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "No content provided in request body");
        }

        if (contentLengthHeader == null || contentLengthHeader.trim().isEmpty()) {
            log.warn("Content-Length header missing - this may cause issues with SAP");
        }
    }

}
