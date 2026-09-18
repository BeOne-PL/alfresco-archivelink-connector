package pl.beone.archivelink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.stereotype.Service;
import pl.beone.archivelink.model.info.ResultAsInfo;
import pl.beone.archivelink.utils.ContentRepositoryService;
import pl.beone.archivelink.utils.DateTimeUtil;

import java.io.IOException;
import java.util.Date;

/**
 * Handles SAP serverInfo function.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d00798f99aa1d6ee10000000a42189c.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkServerInfoService implements ArchiveLinkService {

    private final ContentRepositoryService contRepService;
    @Qualifier("NodeService") private final NodeService nodeService;

    @Value("${sap.pversion}")
    private String pVersion;

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws Exception {
        String contRep = request.getParameter("contRep");
        String pVersion = request.getParameter("pVersion");

        var resultAs = ResultAsInfo.fromString(request.getParameter("resultAs"));

        log.info("contRep={}, pVersion={}, resultAs={}", contRep == null ? "all" : contRep, pVersion, resultAs);

        switch (resultAs) {
            case ASCII: {
                generateAsciiResponse(response, contRep);
                break;
            }
            case HTML: {
                generateHtmlResponse(response, contRep);
                break;
            }
        }
    }

    private void generateAsciiResponse(WebScriptResponse response, String contRep) throws IOException {
        var responseBuffer = new StringBuilder();

        responseBuffer.append("serverStatus=\"running\";");
        responseBuffer.append("serverVendorId=\"").append(getServerVendorId()).append("\";");
        responseBuffer.append("serverVersion=\"").append(getServerVersion()).append("\";");
        responseBuffer.append("serverBuild=\"1.0-SNAPSHOT\";");
        responseBuffer.append("serverTime=\"").append(DateTimeUtil.formatTime(new Date())).append("\";");
        responseBuffer.append("serverDate=\"").append(DateTimeUtil.formatDate(new Date())).append("\";");
        responseBuffer.append("serverStatusDescription=\"running\";");
        responseBuffer.append("pVersion=\"").append(pVersion).append("\";");
        responseBuffer.append("\r\n");

        var contentRepositories = contRepService.getContentRepositoryFolders(contRep);
        for (var contentRepository : contentRepositories) {
            var contRepName = nodeService.getProperty(contentRepository, ContentModel.PROP_NAME);
            var contRepDescr = nodeService.getProperty(contentRepository, ContentModel.PROP_DESCRIPTION);

            responseBuffer.append(String.format("contRep=\"%s\";", contRepName));
            responseBuffer.append(String.format("contRepDescription=\"%s\";", contRepDescr));
            responseBuffer.append("contRepStatus=\"running\";");
            responseBuffer.append("contRepStatusDescription=\"running\";");
        }
        responseBuffer.append("\r\n");

        response.getWriter().write(responseBuffer.toString());
        response.setContentType("text/plain");
        response.setStatus(Status.STATUS_OK);
    }

    private void generateHtmlResponse(WebScriptResponse response, String contRep) throws IOException {
        var responseBuffer = new StringBuilder();

        responseBuffer.append("<!DOCTYPE html>\n");
        responseBuffer.append("<html>\n");
        responseBuffer.append("<head>\n");
        responseBuffer.append("    <title>SAP ArchiveLink Server Info</title>\n");
        responseBuffer.append("    <style>\n");
        responseBuffer.append("        body { font-family: Arial, sans-serif; margin: 20px; }\n");
        responseBuffer.append("        table { border-collapse: collapse; width: 100%; margin: 10px 0; }\n");
        responseBuffer.append("        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        responseBuffer.append("        th { background-color: #f2f2f2; }\n");
        responseBuffer.append("    </style>\n");
        responseBuffer.append("</head>\n");
        responseBuffer.append("<body>\n");

        responseBuffer.append("    <h1>Server Information</h1>\n");
        responseBuffer.append("    <table>\n");
        responseBuffer.append("        <tr><th>Property</th><th>Value</th></tr>\n");
        responseBuffer.append("        <tr><td>Server Status</td><td>Running</td></tr>\n");
        responseBuffer.append("        <tr><td>Server Vendor ID</td><td>").append(getServerVendorId()).append("</td></tr>\n");
        responseBuffer.append("        <tr><td>Server Version</td><td>").append(getServerVersion()).append("</td></tr>\n");
        responseBuffer.append("        <tr><td>Server Build</td><td>1.0-SNAPSHOT</td></tr>\n");
        responseBuffer.append("        <tr><td>Server Time</td><td>").append(DateTimeUtil.formatTime(new Date())).append("</td></tr>\n");
        responseBuffer.append("        <tr><td>Server Date</td><td>").append(DateTimeUtil.formatDate(new Date())).append("</td></tr>\n");
        responseBuffer.append("        <tr><td>Server Description</td><td>running</td></tr>\n");
        responseBuffer.append("        <tr><td>pVersion</td><td>").append(pVersion).append("</td></tr>\n");
        responseBuffer.append("    </table>\n");

        responseBuffer.append("    <h2>Content Repository Information</h2>\n");
        var contentRepositories = contRepService.getContentRepositoryFolders(contRep);
        int idx = 1;
        for (var contentRepository : contentRepositories) {
            var contRepName = nodeService.getProperty(contentRepository, ContentModel.PROP_NAME);
            var contRepDescr = nodeService.getProperty(contentRepository, ContentModel.PROP_DESCRIPTION);

            responseBuffer.append("    <h3>Component ").append(idx++).append("</h3>\n");
            responseBuffer.append("    <table>\n");
            responseBuffer.append("        <tr><th>Property</th><th>Value</th></tr>\n");
            responseBuffer.append("        <tr><td>Content Repository</td><td>").append(contRepName).append("</td></tr>\n");
            responseBuffer.append("        <tr><td>Content Repository Description</td><td>").append(contRepDescr).append("</td></tr>\n");
            responseBuffer.append("        <tr><td>Content Repository Status</td><td>running</td></tr>\n");
            responseBuffer.append("        <tr><td>Content Repository Status Description</td><td>running</td></tr>\n");
            responseBuffer.append("    </table>\n");
        }

        responseBuffer.append("</body>\n");
        responseBuffer.append("</html>\n");

        response.getWriter().write(responseBuffer.toString());
        response.setContentType("text/html");
        response.setStatus(Status.STATUS_OK);
    }

    private String getServerVendorId() {
//        return getClass().getPackage().getImplementationVendor();
        return "BeOne";
    }

    private String getServerVersion() {
//        return getClass().getPackage().getImplementationVersion();
        return "1.0-SNAPSHOT";
    }

}
