package pl.beone.archivelink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.stereotype.Service;
import pl.beone.archivelink.exception.ComponentNotFoundException;
import pl.beone.archivelink.model.SapDocumentModel;
import pl.beone.archivelink.utils.ArchiveLinkFileUtil;
import pl.beone.archivelink.utils.ContentRepositoryService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;

/**
 * Handles SAP update function, HTTP PUT variant.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d064f8a0e33606be10000000a42189e.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkUpdatePutService implements ArchiveLinkService {

    private final ArchiveLinkFileUtil fileUtil;
    private final ContentRepositoryService contRepService;
    @Qualifier("NodeService") private final NodeService nodeService;

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws Exception {
        String contRep = request.getParameter("contRep");
        String compId = request.getParameter("compId");
        String docId = request.getParameter("docId");
        String pVersion = request.getParameter("pVersion");

        var document = contRepService.getDocument(docId, contRep);
        if (compId == null) {
            response.setStatus(Status.STATUS_NOT_FOUND);
            response.getWriter().write("Component not found");
            return;
        }

        log.info("{}/{}/{}", contRep, compId, docId);

        var component = contRepService.getComponent(document, compId)
                .orElseThrow(() -> new ComponentNotFoundException(docId, compId));

        var contentStream = request.getContent().getInputStream();
        String contentLengthHeader = request.getHeader("Content-Length");
        validateContent(contentStream, contentLengthHeader);
        var newData = contentStream.readAllBytes();

        String contentType = request.getContentType();
        String mimeType = fileUtil.determineMimeType(contentType);
        fileUtil.writeContentToNode(mimeType, component, new ByteArrayInputStream(newData));

        nodeService.setProperty(document, ContentModel.PROP_MODIFIED, new Date());

        nodeService.setProperty(component, ContentModel.PROP_SIZE_CURRENT, newData.length);
        nodeService.setProperty(component, ContentModel.PROP_MODIFIED, new Date());
        nodeService.setProperty(component, SapDocumentModel.PROP_SAP_COMPONENT_ID, compId);

        log.info("updated component {}", compId);

        response.setStatus(Status.STATUS_OK);
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
