package pl.beone.archivelink.service;

import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public interface ArchiveLinkService {
    void execute(WebScriptRequest request, WebScriptResponse response) throws Exception;
}
