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
import pl.beone.archivelink.model.SapDocumentModel;
import pl.beone.archivelink.model.info.ComponentInfo;
import pl.beone.archivelink.model.info.DocumentInfo;
import pl.beone.archivelink.model.info.ResultAsInfo;
import pl.beone.archivelink.utils.ArchiveLinkFileUtil;
import pl.beone.archivelink.utils.ContentRepositoryService;
import pl.beone.archivelink.utils.DateTimeUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Handles SAP info function.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d00723399aa1d6ee10000000a42189c.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkInfoService implements ArchiveLinkService {

    @Qualifier("NodeService") private final NodeService nodeService;
    private final ContentRepositoryService contRepService;
    private final ArchiveLinkFileUtil fileUtil;

    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException, ComponentNotFoundException, DocumentNotFoundException {
        String docId = request.getParameter("docId");
        String contRep = request.getParameter("contRep");
        String pVersion = request.getParameter("pVersion");

        String compId = request.getParameter("compId");
        ResultAsInfo resultAs = ResultAsInfo.fromString(request.getParameter("resultAs"));

        log.info("{}/{}/{}, resultAs={}", contRep, docId, compId == null ? "all" : compId, resultAs);

        NodeRef document = contRepService.getDocument(docId, contRep);

        List<ComponentInfo> components = buildComponentsInfo(document, compId, pVersion);

        DocumentInfo docInfo = buildDocumentInfo(document, contRep, docId, pVersion, components.size());

        switch (resultAs) {
            case ASCII: {
                generateAsciiResponse(response, docInfo, components);
                break;
            }
            case HTML: {
                generateHtmlResponse(response, docInfo, components);
                break;
            }
        }
    }

    private void generateAsciiResponse(WebScriptResponse response, DocumentInfo docInfo, List<ComponentInfo> components) throws IOException {
        String boundary = "SAP_ARCHIVELINK_" + System.currentTimeMillis();

        response.setContentType("multipart/form-data; boundary=" + boundary);
        response.setStatus(Status.STATUS_OK);

        response.setHeader("X-dateC", DateTimeUtil.formatDate(docInfo.getDateCreated()));
        response.setHeader("X-timeC", DateTimeUtil.formatTime(docInfo.getDateCreated()));
        response.setHeader("X-dateM", DateTimeUtil.formatDate(docInfo.getDateModified()));
        response.setHeader("X-timeM", DateTimeUtil.formatTime(docInfo.getDateModified()));
        response.setHeader("X-numberComps", String.valueOf(components.size()));
        response.setHeader("X-contentRep", docInfo.getContentRep());
        response.setHeader("X-docId", docInfo.getDocId());
        response.setHeader("X-docStatus", docInfo.getDocStatus());
        response.setHeader("X-pVersion", docInfo.getPVersion());

        Writer writer = response.getWriter();

        for (ComponentInfo component : components) {
            writer.write("--" + boundary + "\n");
            writer.write("Content-Type: " + component.getContentType() +
                    (component.getCharset().isEmpty() ? "" : "; charset=" + component.getCharset()) + "\n");
            writer.write("Content-Length: 0\n");
            writer.write("X-compId: " + component.getCompId() + "\n");
            writer.write("X-Content-Length: " + component.getContentLength() + "\n");
            writer.write("X-compDateC: " + DateTimeUtil.formatDate(component.getCompDateCreated()) + "\n");
            writer.write("X-compTimeC: " + DateTimeUtil.formatTime(component.getCompDateCreated()) + "\n");
            writer.write("X-compDateM: " + DateTimeUtil.formatDate(component.getCompDateModified()) + "\n");
            writer.write("X-compTimeM: " + DateTimeUtil.formatTime(component.getCompDateModified()) + "\n");
            writer.write("X-compStatus: " + component.getCompStatus() + "\n");
            writer.write("X-pVersion: " + component.getPVersion() + "\n");
            writer.write("\n");
        }

        writer.write("--" + boundary + "--\n");
    }

    private void generateHtmlResponse(WebScriptResponse response, DocumentInfo docInfo, List<ComponentInfo> components) throws IOException {
        response.setContentType("text/html; charset=UTF-8");
        response.setStatus(Status.STATUS_OK);

        Writer writer = response.getWriter();

        writer.write("<!DOCTYPE html>\n");
        writer.write("<html>\n");
        writer.write("<head>\n");
        writer.write("    <title>SAP ArchiveLink Document Info</title>\n");
        writer.write("    <style>\n");
        writer.write("        body { font-family: Arial, sans-serif; margin: 20px; }\n");
        writer.write("        table { border-collapse: collapse; width: 100%; margin: 10px 0; }\n");
        writer.write("        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        writer.write("        th { background-color: #f2f2f2; }\n");
        writer.write("    </style>\n");
        writer.write("</head>\n");
        writer.write("<body>\n");

        // Informacje o dokumencie
        writer.write("    <h1>Document Information</h1>\n");
        writer.write("    <table>\n");
        writer.write("        <tr><th>Property</th><th>Value</th></tr>\n");
        writer.write("        <tr><td>Document ID</td><td>" + docInfo.getDocId() + "</td></tr>\n");
        writer.write("        <tr><td>Content Repository</td><td>" + docInfo.getContentRep() + "</td></tr>\n");
        writer.write("        <tr><td>Status</td><td>" + docInfo.getDocStatus() + "</td></tr>\n");
        writer.write("        <tr><td>Created</td><td>" + DateTimeUtil.formatDateTime(docInfo.getDateCreated()) + "</td></tr>\n");
        writer.write("        <tr><td>Modified</td><td>" + DateTimeUtil.formatDateTime(docInfo.getDateModified()) + "</td></tr>\n");
        writer.write("        <tr><td>Number of Components</td><td>" + docInfo.getNumberComps() + "</td></tr>\n");
        writer.write("        <tr><td>Version</td><td>" + docInfo.getPVersion() + "</td></tr>\n");
        writer.write("    </table>\n");

        // Informacje o komponentach
        writer.write("    <h2>Component Information</h2>\n");
        for (int i = 0; i < components.size(); i++) {
            ComponentInfo component = components.get(i);
            writer.write("    <h3>Component " + (i + 1) + "</h3>\n");
            writer.write("    <table>\n");
            writer.write("        <tr><th>Property</th><th>Value</th></tr>\n");
            writer.write("        <tr><td>Component ID</td><td>" + component.getCompId() + "</td></tr>\n");
            writer.write("        <tr><td>Content Type</td><td>" + component.getContentType() + "</td></tr>\n");
            writer.write("        <tr><td>Charset</td><td>" + component.getCharset() + "</td></tr>\n");
            writer.write("        <tr><td>Size</td><td>" + component.getContentLength() + " bytes</td></tr>\n");
            writer.write("        <tr><td>Created</td><td>" + DateTimeUtil.formatDateTime(component.getCompDateCreated()) + "</td></tr>\n");
            writer.write("        <tr><td>Modified</td><td>" + DateTimeUtil.formatDateTime(component.getCompDateModified()) + "</td></tr>\n");
            writer.write("        <tr><td>Status</td><td>" + component.getCompStatus() + "</td></tr>\n");
            writer.write("        <tr><td>Version</td><td>" + component.getPVersion() + "</td></tr>\n");
            writer.write("    </table>\n");
        }

        writer.write("</body>\n");
        writer.write("</html>\n");
    }

    private List<ComponentInfo> buildComponentsInfo(NodeRef document, String compId, String pVersion) throws ComponentNotFoundException {
        List<ComponentInfo> components = new ArrayList<>();

        if (compId != null) {
            NodeRef componentNode = contRepService.getComponent(document, compId)
                    .orElseThrow(() -> new ComponentNotFoundException(nodeService.getProperty(document, SapDocumentModel.PROP_SAP_DOCUMENT_ID).toString(), compId));
            ComponentInfo component = buildSingleComponent(componentNode, compId, pVersion);
            components.add(component);
        } else {
            List<NodeRef> componentNodes = contRepService.getComponents(document);
            for (NodeRef componentNode : componentNodes) {
                String nodeCompId = contRepService.getComponentId(componentNode);
                ComponentInfo component = buildSingleComponent(componentNode, nodeCompId, pVersion);
                components.add(component);
            }
        }

        return components;
    }

    private ComponentInfo buildSingleComponent(NodeRef componentNode, String compId, String pVersion) {
        Date created = (Date) nodeService.getProperty(componentNode, ContentModel.PROP_CREATED);
        Date modified = (Date) nodeService.getProperty(componentNode, ContentModel.PROP_MODIFIED);
        String contentType = fileUtil.getContentType(componentNode);
        String encoding = "UTF-8";
        Long size = (Long) nodeService.getProperty(componentNode, ContentModel.PROP_SIZE_CURRENT);

        return ComponentInfo.builder()
                .compId(compId)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .charset(encoding)
                .contentLength(size != null ? size : 0L)
                .compDateCreated(created)
                .compDateModified(modified)
                .compStatus("online")
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
