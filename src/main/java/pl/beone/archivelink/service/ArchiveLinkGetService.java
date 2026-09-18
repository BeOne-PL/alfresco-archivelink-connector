package pl.beone.archivelink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.stereotype.Service;
import pl.beone.archivelink.exception.ComponentNotFoundException;
import pl.beone.archivelink.exception.DocumentNotFoundException;
import pl.beone.archivelink.utils.ArchiveLinkFileUtil;
import pl.beone.archivelink.utils.ContentRepositoryService;

import java.io.IOException;
import java.util.Optional;

/**
 * Handles SAP get function.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d06fb6f43e05dc6e10000000a42189c.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkGetService implements ArchiveLinkService {

    private final ContentRepositoryService contRepService;
    private final ArchiveLinkFileUtil fileUtil;

    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException, DocumentNotFoundException, ComponentNotFoundException {
        String docId = request.getParameter("docId");
        String contRep = request.getParameter("contRep");

        String compId = request.getParameter("compId");
        Integer fromOffset = getFromOffset(request);
        Integer toOffset = getToOffset(request);

        NodeRef document = contRepService.getDocument(docId, contRep);

        String effectiveCompId = determineComponentId(document, compId)
                .orElseThrow(() -> new ComponentNotFoundException(docId, compId));

        log.info("{}/{}/{}, from={}, to={}", contRep, docId, effectiveCompId, fromOffset, toOffset);

        NodeRef component = contRepService.getComponent(document, effectiveCompId)
                .orElseThrow(() -> new ComponentNotFoundException(docId, compId));

        byte[] content = fileUtil.getNodeContent(component, fromOffset, toOffset);
        String contentType = fileUtil.getContentType(component);

        response.setStatus(Status.STATUS_OK);
        response.setContentType(contentType);
        response.getOutputStream().write(content);
    }

    private static Integer getToOffset(WebScriptRequest webScriptRequest) {
        String toOffset = webScriptRequest.getParameter("toOffset");
        if (toOffset == null) {
            return -1;
        }
        return Integer.parseInt(toOffset);
    }

    private static Integer getFromOffset(WebScriptRequest webScriptRequest) {
        String fromOffset = webScriptRequest.getParameter("fromOffset");
        if (fromOffset == null) {
            return 0;
        }
        return Integer.parseInt(fromOffset);
    }

    /**
     * Determines the componentId based on SAP logic and checks if it exists<br>
     * <ul>
     *     <li>if the componentId is specified, queries and returns that</li>
     *     <li>if the componentId is not specified, checks the "data" componentId</li>
     *     <li>if the component with "data" id doesn't exist, checks the "data1" componentId</li>
     * </ul>
     *
     * @param document Document node
     * @param compId   Request componentId
     * @return Final componentId, or an empty optional if none were found
     */
    private Optional<String> determineComponentId(NodeRef document, String compId) {
        if (compId != null) {
            return contRepService.componentExists(document, compId) ? Optional.of(compId) : Optional.empty();
        }

        if (contRepService.componentExists(document, "data")) {
            return Optional.of("data");
        }
        if (contRepService.componentExists(document, "data1")) {
            return Optional.of("data1");
        }

        return Optional.empty();
    }

}
