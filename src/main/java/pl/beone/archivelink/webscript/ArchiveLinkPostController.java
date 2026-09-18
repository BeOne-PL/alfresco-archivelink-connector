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
import pl.beone.archivelink.service.ArchiveLinkCreatePostService;
import pl.beone.archivelink.service.ArchiveLinkMultiCreateService;
import pl.beone.archivelink.service.ArchiveLinkUpdatePostService;
import pl.beone.archivelink.utils.SecurityUtil;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;

/**
 * WebScript responsible for HTTP POST functions
 */
@Slf4j
@RequiredArgsConstructor
public class ArchiveLinkPostController extends ArchiveLinkController {

    private final ArchiveLinkCreatePostService createService;
    private final ArchiveLinkMultiCreateService multiCreateService;
    private final ArchiveLinkUpdatePostService updateService;

    private final SecurityUtil securityUtil;
    private final ServiceRegistry serviceRegistry;

    @PostConstruct
    private void init() {
        method = HttpMethod.POST;
        serviceMap = Map.of(
                ArchivelinkFunction.CREATE_POST, createService,
                ArchivelinkFunction.M_CREATE, multiCreateService,
                ArchivelinkFunction.UPDATE_POST, updateService
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
                } catch (Exception exception) {
                    log.error("Exception thrown in ArchiveLinkPostController: {}", exception.getMessage());
                    handleError(exception, response);
                }
                return null;
            }, "admin");
            return null;
        }, false, true);
    }

    @Override
    protected void validateRequest(WebScriptRequest request, ArchivelinkFunction function) throws MissingMandatoryParameterException, SAPSecurityException {
        securityUtil.validateMandatoryUrlParameters(request, function);
        securityUtil.validateRequestSecKey(request, function);
    }
}
