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
import pl.beone.archivelink.service.ArchiveLinkAppendService;
import pl.beone.archivelink.service.ArchiveLinkCreatePutService;
import pl.beone.archivelink.service.ArchiveLinkPutCertService;
import pl.beone.archivelink.service.ArchiveLinkUpdatePutService;
import pl.beone.archivelink.utils.SecurityUtil;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;

/**
 * WebScript responsible for HTTP PUT functions
 */
@Slf4j
@RequiredArgsConstructor
public class ArchiveLinkPutController extends ArchiveLinkController {

    private final ArchiveLinkCreatePutService createService;
    private final ArchiveLinkAppendService appendService;
    private final ArchiveLinkUpdatePutService updateService;
    private final ArchiveLinkPutCertService certService;

    private final SecurityUtil securityUtil;
    private final ServiceRegistry serviceRegistry;

    @PostConstruct
    public void init() {
        method = HttpMethod.PUT;
        serviceMap = Map.of(
                ArchivelinkFunction.CREATE_PUT, createService,
                ArchivelinkFunction.APPEND, appendService,
                ArchivelinkFunction.UPDATE_PUT, updateService,
                ArchivelinkFunction.PUT_CERT, certService
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
                    log.error("Exception thrown in ArchiveLinkPutController: {}", e.getMessage());
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
        if (function != ArchivelinkFunction.PUT_CERT) {
            securityUtil.validateRequestSecKey(request, function);
        }
    }
}
