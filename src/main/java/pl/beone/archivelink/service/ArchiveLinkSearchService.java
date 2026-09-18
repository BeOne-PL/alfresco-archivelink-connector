package pl.beone.archivelink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.stereotype.Service;
import pl.beone.archivelink.exception.ComponentNotFoundException;
import pl.beone.archivelink.utils.ArchiveLinkFileUtil;
import pl.beone.archivelink.utils.ContentRepositoryService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles SAP search function.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d0078f299aa1d6ee10000000a42189c.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkSearchService implements ArchiveLinkService {

    private final ContentRepositoryService contRepService;
    private final ArchiveLinkFileUtil fileUtil;

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws Exception {
        String contRep = request.getParameter("contRep");
        String docId = request.getParameter("docId");
        String pattern = request.getParameter("pattern");
        String compId = request.getParameter("compId");
        String pVersion = request.getParameter("pVersion");

        int fromOffset = getFromOffset(request);
        int toOffset = getToOffset(request);
        int numResults = getNumResults(request);
        boolean caseSensitive = isCaseSensitive(request);

        log.info(
                "{}/{}/{}, pattern={}, from={}, to={}, numResults={}, {}",
                contRep, docId, compId, pattern, fromOffset, toOffset, numResults,
                caseSensitive ? "caseSensitive" : "caseInsensitive"
        );

        boolean regularOffsets = fromOffset < toOffset || toOffset == -1;

        var document = contRepService.getDocument(docId, contRep);
        var component = contRepService.getComponent(document, compId)
                .orElseThrow(() -> new ComponentNotFoundException(docId, compId));

        var responseBuffer = new StringBuilder();

        byte[] content = regularOffsets
                ? fileUtil.getNodeContent(component, fromOffset, toOffset)
                : fileUtil.getNodeContent(component, toOffset, fromOffset);
        String contentString = new String(content, StandardCharsets.UTF_8);

        var regexPattern = caseSensitive ? Pattern.compile(pattern) : Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        var matcher = regexPattern.matcher(contentString);

        buildSearchResponse(matcher, responseBuffer, numResults, regularOffsets, fromOffset, toOffset);

        response.setStatus(Status.STATUS_OK);
        response.getWriter().write(responseBuffer.toString());
    }

    private static void buildSearchResponse(Matcher matcher, StringBuilder responseBuffer, int numResults, boolean regularOffsets, int fromOffset, int toOffset) {
        long count = matcher.results().count();
        responseBuffer.append(Math.min(count, numResults)).append(";");
        long logHits = Math.min(count, numResults);
        matcher.reset();

        if (regularOffsets) {
            while (matcher.find() && numResults > 0) {
                responseBuffer.append(matcher.start() + fromOffset).append(";");
                numResults--;
            }
        } else {
            var matchesList = new ArrayList<Integer>();
            while (matcher.find() && numResults > 0) {
                matchesList.add(matcher.start() + toOffset);
                numResults--;
            }

            Collections.reverse(matchesList);
            for (var match : matchesList) {
                responseBuffer.append(match).append(";");
            }
        }

        log.info("{} hits", logHits);
    }

    private static int getFromOffset(WebScriptRequest request) {
        String fromOffset = request.getParameter("fromOffset");
        if (fromOffset == null) {
            return 0;
        }
        return Integer.parseInt(fromOffset);
    }

    private static int getToOffset(WebScriptRequest request) {
        String toOffset = request.getParameter("toOffset");
        if (toOffset == null) {
            return -1;
        }
        return Integer.parseInt(toOffset);
    }

    private static int getNumResults(WebScriptRequest request) {
        String numResults = request.getParameter("numResults");
        if (numResults == null) {
            return 1;
        }
        return Integer.parseInt(numResults);
    }

    private static boolean isCaseSensitive(WebScriptRequest request) {
        String caseSensitive = request.getParameter("caseSensitive");
        if (caseSensitive == null) {
            return false;
        }
        return !caseSensitive.equalsIgnoreCase("n");
    }

}
