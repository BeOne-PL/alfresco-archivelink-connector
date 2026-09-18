package pl.beone.archivelink.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Enum specifying available webscript functions, with additional data for each:<br>
 * - url query command and HTTP method<br>
 * - description<br>
 * - access modes<br>
 * - mandatory and signed parameters
 */
@RequiredArgsConstructor
@Getter
public enum ArchivelinkFunction {

    INFO("info", HttpMethod.GET, "r", "Retrieve information about the document",
            Arrays.asList("contRep", "docId", "pVersion"), Arrays.asList("contRep", "docId")),

    GET("get", HttpMethod.GET, "r", "Fetch (within a range) a content unit of a component",
            Arrays.asList("contRep", "docId", "pVersion"), Arrays.asList("contRep", "docId")),

    DOC_GET("docGet", HttpMethod.GET, "r", "Fetch the entire content of a document",
            Arrays.asList("contRep", "docId", "pVersion"), Arrays.asList("contRep", "docId")),

    CREATE_PUT("create", HttpMethod.PUT, "c", "Create a new document",
            Arrays.asList("contRep", "compId", "docId", "pVersion"), Arrays.asList("contRep", "docId", "compId", "docProt")),

    CREATE_POST("create", HttpMethod.POST, "c", "Create a new document",
            Arrays.asList("contRep", "docId", "pVersion"), Arrays.asList("contRep", "docId", "compId", "docProt")),

    M_CREATE("mCreate", HttpMethod.POST, "c", "Creates a number of new documents",
            Arrays.asList("contRep", "docId", "pVersion"), Arrays.asList("docId", "contRep", "docProt")),

    APPEND("append", HttpMethod.PUT, "u", "Append data",
            Arrays.asList("contRep", "docId", "compId", "pVersion"), Arrays.asList("scanPerformed", "contRep", "docId", "compId")),

    UPDATE_PUT("update", HttpMethod.PUT, "u", "Modify an existing document",
            Arrays.asList("contRep", "docId", "compId", "pVersion"), Arrays.asList("scanPerformed", "contRep", "docId", "compId")),

    UPDATE_POST("update", HttpMethod.POST, "u", "Modify an existing document",
            Arrays.asList("contRep", "docId", "pVersion"), Arrays.asList("scanPerformed", "contRep", "docId", "compId")),

    DELETE("delete", HttpMethod.GET, "d", "Delete a document or component",
            Arrays.asList("contRep", "docId", "pVersion"), Arrays.asList("compId", "contRep", "docId")),

    SEARCH("search", HttpMethod.GET, "r", "Search in a document content",
            Arrays.asList("contRep", "docId", "pVersion", "pattern", "compId"), Arrays.asList("contRep", "docId")),

    ATTR_SEARCH("attrSearch", HttpMethod.GET, "r", "Search for attributes in a document",
            Arrays.asList("contRep", "docId", "pattern", "pVersion"), Arrays.asList("docId", "contRep")),

    PUT_CERT("putCert", HttpMethod.PUT, "", "Set client certificate",
            Arrays.asList("contRep", "authId", "pVersion"), List.of()),

    SERVER_INFO("serverInfo", HttpMethod.GET, "", "Retrieve information about the content server",
            List.of("pVersion"), List.of());

    private final String command;
    private final HttpMethod method;
    private final String accessMode;
    private final String description;
    private final List<String> mandatoryParameters;
    private final List<String> signedParameters;

    /**
     * Extracts function enum from query string
     *
     * @param queryString query string containing a command
     * @return When the query string contains a valid command, a corresponding enum instance. Otherwise, an empty Optional.
     */
    public static Optional<ArchivelinkFunction> fromQueryString(String queryString, HttpMethod method) {
        if (queryString == null) return Optional.empty();

        return Arrays.stream(values())
                .filter(f -> queryString.startsWith(f.command) && f.method == method)
                .findFirst();
    }

}