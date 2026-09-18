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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Date;

/**
 * Handles SAP append function.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d0611060dd02136e10000000a42189e.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkAppendService implements ArchiveLinkService {

    private final ContentRepositoryService contRepService;
    private final ArchiveLinkFileUtil fileUtil;
    @Qualifier("NodeService") private final NodeService nodeService;

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws Exception {
        String contRep = request.getParameter("contRep");
        String docId = request.getParameter("docId");
        String compId = request.getParameter("compId");
        String pVersion = request.getParameter("pVersion");

        log.info("{}/{}/{}", contRep, docId, compId);

        var document = contRepService.getDocument(docId, contRep);
        if (compId == null) {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "No component given");
        }

        var component = contRepService.getComponent(document, compId)
                .orElseThrow(() -> new ComponentNotFoundException(docId, compId));

        var contentStream = request.getContent().getInputStream();
        String contentLengthHeader = request.getHeader("Content-Length");
        validateContent(contentStream, contentLengthHeader);

        var componentContent = fileUtil.getNodeContent(component);
        var outputStream = new ByteArrayOutputStream();
        outputStream.write(componentContent);
        outputStream.write(contentStream.readAllBytes());
        var newData = outputStream.toByteArray();

        String contentType = request.getContentType();
        String mimeType = fileUtil.determineMimeType(contentType);
        fileUtil.writeContentToNode(mimeType, component, new ByteArrayInputStream(newData));

        nodeService.setProperty(document, ContentModel.PROP_MODIFIED, new Date());

        var componentProperties = nodeService.getProperties(component);
        componentProperties.put(ContentModel.PROP_SIZE_CURRENT, newData.length);
        componentProperties.put(ContentModel.PROP_MODIFIED, new Date());
        componentProperties.put(SapDocumentModel.PROP_SAP_COMPONENT_ID, compId);

        nodeService.setProperties(component, componentProperties);

        response.setStatus(Status.STATUS_OK);
        log.info("Appended to component {}", compId);
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
