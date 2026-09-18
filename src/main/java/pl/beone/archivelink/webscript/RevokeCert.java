package pl.beone.archivelink.webscript;

import lombok.RequiredArgsConstructor;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import pl.beone.archivelink.model.SapCertModel;
import pl.beone.archivelink.utils.SapPublicKeyUtil;

import java.io.IOException;
import java.util.Optional;

@RequiredArgsConstructor
public class RevokeCert extends AbstractWebScript {

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
        nodeService.setProperty(certNode.get(), SapCertModel.PROP_IS_ACCEPTED, false);
    }
}
