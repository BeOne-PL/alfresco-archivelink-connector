package pl.beone.archivelink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.stereotype.Service;
import pl.beone.archivelink.exception.ComponentNotFoundException;
import pl.beone.archivelink.exception.DocumentNotFoundException;
import pl.beone.archivelink.model.info.ComponentInfo;
import pl.beone.archivelink.model.info.DocumentInfo;
import pl.beone.archivelink.utils.ArchiveLinkFileUtil;
import pl.beone.archivelink.utils.ContentRepositoryService;
import pl.beone.archivelink.utils.DateTimeUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Handles SAP docGet function.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d063b424132468de10000000a42189c.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkDocGetService implements ArchiveLinkService {

    private final ArchiveLinkFileUtil fileUtil;
    private final ContentRepositoryService contRepService;
    @Qualifier("NodeService") private final NodeService nodeService;

    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException, DocumentNotFoundException, ComponentNotFoundException {
        String docId = request.getParameter("docId");
        String contRep = request.getParameter("contRep");
        String pVersion = request.getParameter("pVersion");

        log.info("{}/{}", contRep, docId);

        NodeRef document = contRepService.getDocument(docId, contRep);

        List<ComponentInfo> components = buildComponentsInfo(document, pVersion);
        DocumentInfo docInfo = buildDocumentInfo(document, contRep, docId, pVersion, components.size());

        generateMultipartResponse(response, docInfo, components);
    }

    private void generateMultipartResponse(WebScriptResponse response, DocumentInfo docInfo, List<ComponentInfo> components)
            throws IOException {
        String boundary = "SAP_ARCHIVELINK_" + System.currentTimeMillis();

        response.setContentType("multipart/form-data; boundary=" + boundary);
        response.setStatus(Status.STATUS_OK);

        response.setHeader("X-dateC", DateTimeUtil.formatDate(docInfo.getDateCreated()));
        response.setHeader("X-timeC", DateTimeUtil.formatTime(docInfo.getDateCreated()));
        response.setHeader("X-dateM", DateTimeUtil.formatDate(docInfo.getDateModified()));
        response.setHeader("X-timeM", DateTimeUtil.formatTime(docInfo.getDateModified()));
        response.setHeader("X-numComps", String.valueOf(components.size()));
        response.setHeader("X-contRep", docInfo.getContentRep());
        response.setHeader("X-docId", docInfo.getDocId());
        response.setHeader("X-docStatus", docInfo.getDocStatus());
        response.setHeader("X-pVersion", docInfo.getPVersion());

        OutputStream outputStream = response.getOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), false);

        for (ComponentInfo component : components) {
            generateComponentMultipartData(component, writer, boundary);
        }

        writer.println("--" + boundary + "--");
        writer.flush();
    }

    private static void generateComponentMultipartData(ComponentInfo component, PrintWriter writer, String boundary) {
        writer.println("--" + boundary);

        String contentType = component.getContentType();
        if (!component.getCharset().isEmpty()) {
            contentType += "; charset=" + component.getCharset();
        }
        writer.println("Content-Type: " + contentType);
        writer.println("Content-Length: " + component.getContentLength());
        writer.println("X-Content-Length: " + component.getContentLength());
        writer.println("X-compId: " + component.getCompId());
        writer.println("X-compDateC: " + DateTimeUtil.formatDate(component.getCompDateCreated()));
        writer.println("X-compTimeC: " + DateTimeUtil.formatTime(component.getCompDateCreated()));
        writer.println("X-compDateM: " + DateTimeUtil.formatDate(component.getCompDateModified()));
        writer.println("X-compTimeM: " + DateTimeUtil.formatTime(component.getCompDateModified()));
        writer.println("X-compStatus: " + component.getCompStatus());
        writer.println("X-pVersion: " + component.getPVersion());
        writer.println();

        writer.flush();

        if (component.getContent() != null && !component.getContent().isEmpty()) {
            writer.print(component.getContent());
            writer.flush();
        }

        writer.println();
    }

    private List<ComponentInfo> buildComponentsInfo(NodeRef document, String pVersion) {
        List<ComponentInfo> components = new ArrayList<>();

        List<NodeRef> componentNodes = contRepService.getComponents(document);
        for (NodeRef componentNode : componentNodes) {
            String nodeCompId = contRepService.getComponentId(componentNode);
            ComponentInfo component = buildSingleComponent(componentNode, nodeCompId, pVersion);
            components.add(component);
        }

        return components;
    }

    private ComponentInfo buildSingleComponent(NodeRef componentNode, String compId, String pVersion) {
        Date created = (Date) nodeService.getProperty(componentNode, ContentModel.PROP_CREATED);
        Date modified = (Date) nodeService.getProperty(componentNode, ContentModel.PROP_MODIFIED);
        String contentType = fileUtil.getContentType(componentNode);
        String encoding = "UTF-8";
        byte[] contentBytes = fileUtil.getNodeContent(componentNode);
        String content = new String(contentBytes, StandardCharsets.UTF_8);
        long contentLength = contentBytes.length;

        return ComponentInfo.builder()
                .compId(compId)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .charset(encoding)
                .contentLength(contentLength)
                .compDateCreated(created)
                .compDateModified(modified)
                .compStatus("online")
                .content(content)
                .pVersion(pVersion)
                .build();
    }

    private DocumentInfo buildDocumentInfo(NodeRef document, String contRep, String docId, String pVersion, int componentCount) {
        Date created = (Date) nodeService.getProperty(document, ContentModel.PROP_CREATED);
        Date modified = (Date) nodeService.getProperty(document, ContentModel.PROP_MODIFIED);

        return DocumentInfo.builder()
                .dateCreated(created)
                .dateModified(modified)
                .contentRep(contRep)
                .docId(docId)
                .docStatus("online")
                .pVersion(pVersion)
                .numberComps(componentCount)
                .build();
    }

}
