package pl.beone.archivelink.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.stereotype.Service;
import pl.beone.archivelink.exception.ComponentNotFoundException;
import pl.beone.archivelink.utils.ArchiveLinkFileUtil;
import pl.beone.archivelink.utils.ContentRepositoryService;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Handles SAP attrSearch function.
 * <a href="https://help.sap.com/docs/ABAP_PLATFORM_NEW/3ad3ba0715c5422eae08578d4c40328d/4d01181264a82102e10000000a42189e.html?locale=en-US">Documentation</a>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveLinkAttrSearchService implements ArchiveLinkService {

    private final ContentRepositoryService contRepService;
    private final ArchiveLinkFileUtil fileUtil;

    @Override
    public void execute(WebScriptRequest request, WebScriptResponse response) throws Exception {
        String contRep = request.getParameter("contRep");
        String docId = request.getParameter("docId");
        String pattern = request.getParameter("pattern");
        String pVersion = request.getParameter("pVersion");

        boolean isCaseSensitive = isCaseSensitive(request);
        int fromOffset = getFromOffset(request);
        int toOffset = getToOffset(request);
        int numResults = getNumResults(request);

        log.info("{}/{}, pattern={}, caseSensitive={}, from={}, to={}, numResults={}",
                contRep, docId, pattern, isCaseSensitive, fromOffset, toOffset, numResults);

        var document = contRepService.getDocument(docId, contRep);

        if (!contRepService.componentExists(document, "data")) {
            throw new ComponentNotFoundException(docId, "data");
        }

        var description = contRepService.getComponent(document, "descr")
                .orElseThrow(() -> new ComponentNotFoundException(docId, "descr"));

        byte[] descriptionContent = fileUtil.getNodeContent(description, fromOffset, toOffset);
        String descriptionString = new String(descriptionContent, StandardCharsets.UTF_8);

        var parsedPattern = parseRequestPattern(pattern);
        var parsedDescription = parseDescriptionFile(descriptionString);

        var attrSearchResult = getAttrSearchResult(parsedDescription, parsedPattern, numResults, isCaseSensitive);

        response.setStatus(Status.STATUS_OK);
        response.getWriter().write(attrSearchResult.response);
        log.info("{} hits", attrSearchResult.hits);
    }

    private Map<Pair<Integer, Integer>, String> parseRequestPattern(String pattern) {
        var result = new HashMap<Pair<Integer, Integer>, String>();

        var attributesPatterns = pattern.split("#");
        for (var attribute : attributesPatterns) {
            var attributePattern = attribute.split("\\+");
            if (attributePattern.length != 3) {
                throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Invalid pattern");
            }
            result.put(Pair.with(Integer.parseInt(attributePattern[0]), Integer.parseInt(attributePattern[1])), attributePattern[2]);
        }

        return result;
    }

    private List<DescriptionResult> parseDescriptionFile(String descriptionFile) {
        var fileScanner = new Scanner(descriptionFile);

        var attrDescription = new LinkedHashMap<String, Pair<Integer, Integer>>();
        var results = new ArrayList<DescriptionResult>();

        while (fileScanner.hasNextLine()) {
            String line = fileScanner.nextLine();
            if (line.trim().isEmpty()) continue;

            var lineContent = line.split("\\s+", 3);
            if (lineContent.length != 3) continue;

            int offsetInFile = Integer.parseInt(lineContent[0]);
            int lengthInFile = Integer.parseInt(lineContent[1]);
            String recordType = lineContent[2].substring(0, 4);
            String remainingData = lineContent[2].substring(4);

            switch (recordType) {
                case "DKEY": {
                    String attributeName = remainingData.substring(0, 40).trim();
                    int offsetInLine = Integer.parseInt(remainingData.substring(40, 43).trim());
                    int lengthInLine = Integer.parseInt(remainingData.substring(44, 46).trim());
                    attrDescription.put(attributeName, Pair.with(offsetInLine, lengthInLine));
                    break;
                }
                case "DAIN": {
                    String parameter = remainingData.trim();
                    var attributeValues = new HashMap<Pair<Integer, Integer>, String>();

                    for (var attribute : attrDescription.entrySet()) {
                        var attributeName = attribute.getKey();
                        var attributeData = attribute.getValue();
                        var attributeStartIdx = attributeData.getValue0();
                        var attributeLength = attributeData.getValue1();

                        var attributeValue = parameter.substring(attributeStartIdx, Math.min(attributeStartIdx + attributeLength, remainingData.length())).trim();
                        attributeValues.put(attrDescription.get(attributeName), attributeValue);
                    }

                    var result = DescriptionResult.builder()
                            .offset(offsetInFile)
                            .length(lengthInFile)
                            .attributes(attributeValues)
                            .build();
                    results.add(result);

                    break;
                }
            }
        }

        return results;
    }

    private static AttrSearchResult getAttrSearchResult(
            List<DescriptionResult> parsedDescription,
            Map<Pair<Integer, Integer>, String> parsedPattern,
            int numResults,
            boolean isCaseSensitive
    ) {
        var responseBuffer = new StringBuilder();
        int hits = 0;

        descrLineLoop:
        for (var descriptionLine : parsedDescription) {
            if (hits >= numResults) break;

            for (var patternPart : parsedPattern.entrySet()) {
                var patternData = patternPart.getKey();
                var patternStr = patternPart.getValue();

                int patternStartIdx = patternData.getValue0();
                int patternLength = patternData.getValue1();
                var matchingDescrAttrValue = descriptionLine.attributes.get(Pair.with(patternStartIdx, patternLength));
                if (matchingDescrAttrValue == null) {
                    continue descrLineLoop;
                }

                var regexPattern = isCaseSensitive
                        ? Pattern.compile(patternStr)
                        : Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                var matcher = regexPattern.matcher(matchingDescrAttrValue);

                if (!matcher.find()) {
                    continue descrLineLoop;
                }
            }

            responseBuffer
                    .append(descriptionLine.offset).append(';')
                    .append(descriptionLine.length).append(';');
            hits++;
        }

        responseBuffer.insert(0, hits + ";");

        return AttrSearchResult.builder()
                .response(responseBuffer.toString())
                .hits(hits)
                .build();
    }

    private static boolean isCaseSensitive(WebScriptRequest request) {
        String caseSensitive = request.getParameter("caseSensitive");
        if (caseSensitive == null) {
            return false;
        }
        return !caseSensitive.equalsIgnoreCase("n");
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

    @Data
    @Builder
    private static class AttrSearchResult {
        public final String response;
        public final int hits;
    }

    @Data
    @Builder
    private static class DescriptionResult {
        private int offset;
        private int length;
        private HashMap<Pair<Integer, Integer>, String> attributes;
    }

}