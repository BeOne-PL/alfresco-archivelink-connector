package pl.beone.archivelink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.repository.NodeService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.stereotype.Service;
import pl.beone.archivelink.exception.ComponentNotFoundException;
import pl.beone.archivelink.utils.ContentRepositoryService;

/**
 * Handles SAP delete function.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d057a8eeaa85c4be10000000a42189e.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkDeleteService implements ArchiveLinkService {

    private final ContentRepositoryService contRepService;
    @Qualifier("NodeService") private final NodeService nodeService;

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws Exception {
        String contRep = request.getParameter("contRep");
        String docId = request.getParameter("docId");
        String compId = request.getParameter("compId");
        String pVersion = request.getParameter("pVersion");

        log.info("{}/{}/{}", contRep, docId, compId == null ? "null" : compId);

        var document = contRepService.getDocument(docId, contRep);

        if (compId != null) {
            var component = contRepService.getComponent(document, compId)
                    .orElseThrow(() -> new ComponentNotFoundException(docId, compId));
            nodeService.deleteNode(component);
            log.info("removed component {}", compId);
        } else {
            nodeService.deleteNode(document);
            log.info("removed document {}", docId);
        }

        response.setStatus(Status.STATUS_OK);
    }

}
