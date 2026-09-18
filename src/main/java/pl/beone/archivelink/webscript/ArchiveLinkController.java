package pl.beone.archivelink.webscript;

import lombok.extern.slf4j.Slf4j;
import org.springframework.extensions.webscripts.*;
import org.springframework.http.HttpMethod;
import pl.beone.archivelink.exception.*;
import pl.beone.archivelink.model.ArchivelinkFunction;
import pl.beone.archivelink.service.ArchiveLinkService;

import java.io.IOException;
import java.util.Map;

/**
 * Abstract "controller" Webscript class with some common parameters and methods
 */
@Slf4j
public abstract class ArchiveLinkController extends AbstractWebScript {

    protected HttpMethod method;
    protected Map<ArchivelinkFunction, ArchiveLinkService> serviceMap;

    /**
     * Parses the received URL query string and tries to extract a requested function
     *
     * @param queryString Request URL query string
     * @return SAP function for given query and controller method
     * @throws IllegalArgumentException If the method isn't supported
     */
    protected ArchivelinkFunction parseFunction(String queryString) {
        return ArchivelinkFunction.fromQueryString(queryString, method)
                .orElseThrow(() -> new IllegalArgumentException("Unknown function: " + method + " " + queryString));
    }

    /**
     * Finds a service for a given function and executes it's functionality
     *
     * @param function Requested SAP function
     * @param request  WebScript request
     * @param response WebScript response
     * @throws Exception                     When the service encounters a problem
     * @throws UnsupportedOperationException When a service for a function isn't implemented
     */
    protected void executeService(ArchivelinkFunction function, WebScriptRequest request, WebScriptResponse response) throws Exception {
        ArchiveLinkService service = serviceMap.get(function);
        if (service == null) {
            throw new UnsupportedOperationException("Service not implemented for: " + function.getCommand());
        }
        service.execute(request, response);
    }

    protected abstract void validateRequest(WebScriptRequest request, ArchivelinkFunction function) throws MissingMandatoryParameterException, SAPSecurityException;

    /**
     * Builds an appropriate response for a given exception
     *
     * @param exception Exception thrown by a controller or any of the services
     * @param response  WebScript response
     * @throws IOException When requesting the response writer fails (should never happen)
     */
    protected void handleError(Exception exception, WebScriptResponse response) throws IOException {
        if (exception instanceof WebScriptException) {
            response.setStatus(((WebScriptException) exception).getStatus());
            response.getWriter().write(exception.getMessage());
            log.error("WebScript exception: {}", exception.getMessage());
            log.debug("Full stack trace", exception);
        } else if (exception instanceof IllegalArgumentException) {
            response.setStatus(Status.STATUS_BAD_REQUEST);
            response.getWriter().write(exception.getMessage());
            log.error("400 Bad Request: {}", exception.getMessage());
            log.debug("Full stack trace", exception);
        } else if (exception instanceof SAPSecurityException) {
            response.setStatus(Status.STATUS_UNAUTHORIZED);
            response.getWriter().write(exception.getMessage());
            log.error("401 Unauthorized: {}", exception.getMessage());
            log.debug("Full stack trace", exception);
        } else if (exception instanceof DuplicatedDocumentException) {
            response.setStatus(Status.STATUS_FORBIDDEN);
            response.getWriter().write(exception.getMessage());
            log.error("403 Forbidden: {}", exception.getMessage());
            log.debug("Full stack trace", exception);
        } else if (exception instanceof DocumentNotFoundException || exception instanceof ComponentNotFoundException || exception instanceof MissingMandatoryParameterException) {
            response.setStatus(Status.STATUS_NOT_FOUND);
            response.getWriter().write(exception.getMessage());
            log.error("404 Not Found: {}", exception.getMessage());
            log.debug("Full stack trace", exception);
        } else {
            response.setStatus(Status.STATUS_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Internal server error");
            log.error("500 Internal Server Error (Global error)", exception);
        }
    }

}
