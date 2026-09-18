package pl.beone.archivelink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.fileupload2.core.FileItem;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.extensions.webscripts.servlet.FormData;
import org.springframework.stereotype.Service;
import pl.beone.archivelink.utils.ArchiveLinkFileUtil;
import pl.beone.archivelink.utils.ContentRepositoryService;
import pl.beone.archivelink.utils.MultiPartFormUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Handles SAP mCreate function.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d00755b99aa1d6ee10000000a42189c.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkMultiCreateService implements ArchiveLinkService {

    private final ContentRepositoryService contRepService;
    private final ArchiveLinkFileUtil fileUtil;

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws Exception {
        String contRep = request.getParameter("contRep");
        String pVersion = request.getParameter("pVersion");
        String docProt = request.getParameter("docProt");

        log.info("contRep={}", contRep);

        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/form-data")) {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Incorrect content type, expected multipart/form-data");
        }

        var contentRepository = contRepService.getOrCreateContentRepositoryFolder(contRep);
        var targetFolder = contRepService.getOrCreateFolderStructure(contentRepository);

        var formDataFields = MultiPartFormUtil.extractFormFields(request);

        var docIds = collectDocIds(formDataFields);
        docIds.forEach(docId -> contRepService.validateDuplicate(docId, contRep));

        var documentMap = new HashMap<String, NodeRef>();
        docIds.forEach(docId -> {
            var documentNode = contRepService.createDocument(targetFolder, docId, contRep, docProt, pVersion);
            documentMap.put(docId, documentNode);
        });

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

                String docId = fileItem.getHeaders().getHeader("X-docId");
                var documentNode = documentMap.get(docId);

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

    private List<String> collectDocIds(FormData.FormField[] formDataFields) {
        return Arrays.stream(formDataFields)
                .map(field -> {
                    if (!field.getIsFile()) return null;

                    try {
                        var privateFileItemField = field.getClass().getDeclaredField("file");
                        privateFileItemField.setAccessible(true);
                        var fileItem = (FileItem) privateFileItemField.get(field);

                        String docId = fileItem.getHeaders().getHeader("X-docId");
                        if (docId == null || docId.trim().isEmpty()) {
                            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Missing required header: X-docId");
                        }
                        return docId;
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        throw new WebScriptException(Status.STATUS_INTERNAL_SERVER_ERROR, "Failed to extract part headers via reflection.");
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

}
