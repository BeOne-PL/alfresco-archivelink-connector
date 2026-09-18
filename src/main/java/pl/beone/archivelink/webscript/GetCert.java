package pl.beone.archivelink.webscript;

import lombok.RequiredArgsConstructor;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.*;
import pl.beone.archivelink.model.SapCertModel;
import pl.beone.archivelink.utils.SapPublicKeyUtil;

import java.io.IOException;
import java.util.Optional;

@RequiredArgsConstructor
public class GetCert extends AbstractWebScript {

    private final SapPublicKeyUtil publicKeyUtil;
    @Qualifier("ContentService")
    private final ContentService contentService;
    @Qualifier("NodeService")
    private final NodeService nodeService;

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {
        String contRep = req.getParameter("contRep");
        var certFolder = publicKeyUtil.getOrCreateCertificateFolder(contRep);
        Optional<NodeRef> certNode = publicKeyUtil.getPublicKey(certFolder);
        if(certNode.isEmpty()) {
            res.setStatus(Status.STATUS_NOT_FOUND);
            return;
        }
        ContentReader reader = contentService.getReader(certNode.get(), ContentModel.PROP_CONTENT);
        reader.getContent(res.getOutputStream());
        String filename = (String) nodeService.getProperty(certNode.get(), ContentModel.PROP_NAME);
        res.setHeader("Content-Disposition", "attachment;filename=\"" + filename + "\"");
    }
}
