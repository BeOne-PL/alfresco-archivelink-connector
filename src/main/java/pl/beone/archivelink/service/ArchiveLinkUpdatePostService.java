package pl.beone.archivelink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.fileupload2.core.FileItem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.extensions.webscripts.servlet.FormData;
import org.springframework.stereotype.Service;
import pl.beone.archivelink.exception.ComponentNotFoundException;
import pl.beone.archivelink.model.SapDocumentModel;
import pl.beone.archivelink.utils.ArchiveLinkFileUtil;
import pl.beone.archivelink.utils.ContentRepositoryService;
import pl.beone.archivelink.utils.MultiPartFormUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Handles SAP update function, HTTP POST variant.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d007a8c99aa1d6ee10000000a42189c.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkUpdatePostService implements ArchiveLinkService {

    private final ContentRepositoryService contRepService;
    private final ArchiveLinkFileUtil fileUtil;
    @Qualifier("NodeService") private final NodeService nodeService;

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws Exception {
        String contRep = request.getParameter("contRep");
        String docId = request.getParameter("docId");
        String pVersion = request.getParameter("pVersion");

        log.info("{}/{}", contRep, docId);

        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/form-data")) {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Incorrect content type, expected multipart/form-data");
        }

        var formDataFields = MultiPartFormUtil.extractFormFields(request);

        var document = contRepService.getDocument(docId, contRep);

        var currentComponentIds = contRepService.getComponents(document)
                .stream()
                .map(contRepService::getComponentId)
                .collect(Collectors.toSet());

        for (var field : formDataFields) {
            if (!field.getIsFile()) continue;

            String fieldContentType = field.getMimetype();
            String mimeType = fileUtil.determineMimeType(fieldContentType);

            try {
                var privateFileItemField = field.getClass().getDeclaredField("file");

                privateFileItemField.setAccessible(true);
                var fileItem = (FileItem) privateFileItemField.get(field);

                String compId = fileItem.getHeaders().getHeader("X-compId");
                if (compId == null || compId.trim().isEmpty()) {
                    throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Missing required header: X-compId");
                }

                String contentLength = fileItem.getHeaders().getHeader("Content-Length");
                if (contentLength == null || contentLength.trim().isEmpty()) {
                    throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Missing required header: Content-Length");
                }

                if (currentComponentIds.contains(compId)) {
                    overwriteExistingComponent(field, document, compId, docId, mimeType);
                    currentComponentIds.remove(compId);
                    log.info("updated component {}", compId);
                } else {
                    contRepService.createDocumentComponent(document, compId, mimeType, field.getInputStream(), contRep, docId, pVersion);
                    log.info("created component {}", compId);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new WebScriptException(Status.STATUS_INTERNAL_SERVER_ERROR, "Failed to extract part headers via reflection.", e);
            }
        }

        for (var remainingCompId : currentComponentIds) {
            var component = contRepService.getComponent(document, remainingCompId);
            if (component.isEmpty()) continue;
            nodeService.deleteNode(component.get());
        }

        response.setStatus(Status.STATUS_OK);
    }

    private void overwriteExistingComponent(FormData.FormField field, NodeRef document, String compId, String docId, String mimeType) throws IOException {
        var component = contRepService.getComponent(document, compId)
                .orElseThrow(() -> new ComponentNotFoundException(docId, compId));

        var contentStream = field.getContent().getInputStream();
        validateContent(contentStream, mimeType);
        var newData = contentStream.readAllBytes();

        fileUtil.writeContentToNode(mimeType, component, new ByteArrayInputStream(newData));

        nodeService.setProperty(document, ContentModel.PROP_MODIFIED, new Date());

        nodeService.setProperty(component, ContentModel.PROP_SIZE_CURRENT, newData.length);
        nodeService.setProperty(component, ContentModel.PROP_MODIFIED, new Date());
        nodeService.setProperty(component, SapDocumentModel.PROP_SAP_COMPONENT_ID, compId);
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
