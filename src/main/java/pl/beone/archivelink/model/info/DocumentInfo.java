package pl.beone.archivelink.model.info;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class DocumentInfo {
    private String contentType;
    private Long contentLength;
    private Date dateCreated;
    private Date dateModified;
    private String contentRep;
    private String docId;
    private String docStatus;
    private String pVersion;
    private Integer numberComps;
}
