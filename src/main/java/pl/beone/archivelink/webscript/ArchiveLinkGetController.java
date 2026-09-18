package pl.beone.archivelink.webscript;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.http.HttpMethod;
import pl.beone.archivelink.exception.MissingMandatoryParameterException;
import pl.beone.archivelink.exception.SAPSecurityException;
import pl.beone.archivelink.model.ArchivelinkFunction;
import pl.beone.archivelink.service.*;
import pl.beone.archivelink.utils.SecurityUtil;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;

/**
 * WebScript responsible for HTTP GET functions
 */
@Slf4j
@RequiredArgsConstructor
public class ArchiveLinkGetController extends ArchiveLinkController {

    private final ArchiveLinkGetService getService;
    private final ArchiveLinkDocGetService docService;
    private final ArchiveLinkInfoService infoService;
    private final ArchiveLinkDeleteService deleteService;
    private final ArchiveLinkSearchService searchService;
    private final ArchiveLinkAttrSearchService attrSearchService;
    private final ArchiveLinkServerInfoService serverInfoService;

    private final SecurityUtil securityUtil;

    private final ServiceRegistry serviceRegistry;

    @PostConstruct
    public void init() {
        method = HttpMethod.GET;
        serviceMap = Map.of(
                ArchivelinkFunction.INFO, infoService,
                ArchivelinkFunction.GET, getService,
                ArchivelinkFunction.DOC_GET, docService,
                ArchivelinkFunction.DELETE, deleteService,
                ArchivelinkFunction.SEARCH, searchService,
                ArchivelinkFunction.ATTR_SEARCH, attrSearchService,
                ArchivelinkFunction.SERVER_INFO, serverInfoService
        );
    }

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        serviceRegistry.getRetryingTransactionHelper().doInTransaction(() -> {
            AuthenticationUtil.runAs(() -> {
                try {
                    ArchivelinkFunction function = parseFunction(request.getQueryString());
                    validateRequest(request, function);
                    executeService(function, request, response);
                } catch (Exception e) {
                    log.error("Exception thrown in ArchiveLinkGetController: {}", e.getMessage());
                    handleError(e, response);
                }
                return null;
            }, "admin");
            return null;
        }, false, true);
    }

    @Override
    protected void validateRequest(WebScriptRequest request, ArchivelinkFunction function) throws MissingMandatoryParameterException, SAPSecurityException {
        securityUtil.validateMandatoryUrlParameters(request, function);
        if (function != ArchivelinkFunction.SERVER_INFO) {
            securityUtil.validateRequestSecKey(request, function);
        }
    }
}
