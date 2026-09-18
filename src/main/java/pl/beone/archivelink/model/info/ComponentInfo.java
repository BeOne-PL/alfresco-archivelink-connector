package pl.beone.archivelink.model.info;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class ComponentInfo {
    private String compId;
    private String contentType;
    private String charset;
    private Long contentLength;
    private Date compDateCreated;
    private Date compDateModified;
    private String compStatus;
    private String pVersion;
    private String content;
}