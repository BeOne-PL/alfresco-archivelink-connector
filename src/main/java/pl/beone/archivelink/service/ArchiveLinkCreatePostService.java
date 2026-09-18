package pl.beone.archivelink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload2.core.FileItem;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.stereotype.Service;
import pl.beone.archivelink.utils.ArchiveLinkFileUtil;
import pl.beone.archivelink.utils.ContentRepositoryService;
import pl.beone.archivelink.utils.MultiPartFormUtil;

/**
 * Handles SAP create function, HTTP POST variant.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d057599eaa85c4be10000000a42189e.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkCreatePostService implements ArchiveLinkService {

    private final ArchiveLinkFileUtil fileUtil;
    private final ContentRepositoryService contRepService;
//    private final SynchronizationService synchronizationService;

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws Exception {
        String contRep = request.getParameter("contRep");
        String docId = request.getParameter("docId");
        String pVersion = request.getParameter("pVersion");
        String docProt = request.getParameter("docProt");

        log.info("{}/{}", contRep, docId);

        contRepService.validateDuplicate(docId, contRep);

        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/form-data")) {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Incorrect content type, expected multipart/form-data");
        }

        var contentRepository = contRepService.getOrCreateContentRepositoryFolder(contRep);
        var targetFolder = contRepService.getOrCreateFolderStructure(contentRepository);

        var formDataFields = MultiPartFormUtil.extractFormFields(request);

        var documentNode = contRepService.createDocument(targetFolder, docId, contRep, docProt, pVersion);

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

                if (contRepService.componentExists(documentNode, compId)) {
                    throw new WebScriptException(Status.STATUS_FORBIDDEN, "Component already exists");
                }

                contRepService.createDocumentComponent(documentNode, compId, mimeType, field.getInputStream(), contRep, docId, pVersion);

                log.info("doc created: docId={}, compId={}", docId, compId);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new WebScriptException(Status.STATUS_INTERNAL_SERVER_ERROR, "Failed to extract part headers via reflection.", e);
            }
        }

//        synchronizationService.waitForEndOfUpdate();

        response.setStatus(Status.STATUS_CREATED);
    }

}
