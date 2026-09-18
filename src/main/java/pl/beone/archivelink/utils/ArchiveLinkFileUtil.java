package pl.beone.archivelink.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class ArchiveLinkFileUtil {

    @Qualifier("NodeService") private final NodeService nodeService;
    @Qualifier("ContentService") private final ContentService contentService;

    /**
     * Gets a folder by its name
     *
     * @param parent     Folder's parent node
     * @param folderName Folder's name
     * @return An optional with a reference to a folder node, or empty if not found
     */
    public Optional<NodeRef> getFolder(NodeRef parent, String folderName) {
        List<ChildAssociationRef> childRefs = nodeService.getChildAssocs(parent);
        for (ChildAssociationRef childRef : childRefs) {
            NodeRef childNodeRef = childRef.getChildRef();
            String childNodeName = (String) nodeService.getProperty(childNodeRef, ContentModel.PROP_NAME);
            if (folderName.equals(childNodeName)) {
                return Optional.ofNullable(childNodeRef);
            }
        }

        return Optional.empty();
    }

    /**
     * Creates a folder under a given parent
     *
     * @param parent      Folder's parent node
     * @param folderName  Folder node name
     * @param folderTitle Folder node title
     * @param assocQName  Qualified name of folder-parent association
     * @return Reference to folder node
     */
    public NodeRef createFolder(NodeRef parent, String folderName, String folderTitle, QName assocQName) {
        var props = new HashMap<QName, Serializable>();
        props.put(ContentModel.PROP_NAME, folderName);
        props.put(ContentModel.PROP_TITLE, folderTitle);

        var newFolder = nodeService.createNode(
                parent,
                ContentModel.ASSOC_CONTAINS,
                assocQName,
                ContentModel.TYPE_FOLDER,
                props
        );

        return newFolder.getChildRef();
    }

    /**
     * Returns document's MIME type
     *
     * @param componentNode Component node
     * @return Document's MIME type, or application/octet-stream (if null or empty)
     */
    public String getContentType(NodeRef componentNode) {
        ContentReader contentReader = contentService.getReader(componentNode, ContentModel.PROP_CONTENT);
        if (contentReader != null && contentReader.exists()) {
            return contentReader.getMimetype();
        }
        return "application/octet-stream";
    }

    /**
     * @param contentType MIME content type string
     * @return MIME content type String, or a default BINARY (if null or empty)
     */
    public String determineMimeType(String contentType) {
        if (contentType != null && !contentType.trim().isEmpty()) {
            return contentType;
        }

        // SAP components often lack a file extension, so default to binary
        return MimetypeMap.MIMETYPE_BINARY;
    }

    /**
     * Writes content to a preexisting node
     *
     * @param mimeType Content MIME type
     * @param file     Node to write to
     * @param content  Content to write
     */
    public void writeContentToNode(String mimeType, NodeRef file, InputStream content) {
        ContentWriter writer = contentService.getWriter(file, ContentModel.PROP_CONTENT, true);
        writer.setMimetype(mimeType);
        writer.putContent(content);
    }

    /**
     * Returns content of a node with default offsets
     *
     * @param component Component node
     * @return A byte array containing component content
     */
    public byte[] getNodeContent(NodeRef component) {
        return getNodeContent(component, 0, -1);
    }

    /**
     * Returns content of a node with given offsets
     *
     * @param component  Component node
     * @param fromOffset Starting offset
     * @param toOffset   Ending offset
     * @return A byte array containing component content
     */
    public byte[] getNodeContent(NodeRef component, Integer fromOffset, Integer toOffset) {
        try {
            ContentReader contentReader = contentService.getReader(component, ContentModel.PROP_CONTENT);

            // If no offsets are given, return the whole content
            if ((fromOffset == null || fromOffset <= 0)
                    && (toOffset == null || toOffset < 0)) {
                return contentReader.getContentInputStream().readAllBytes();
            }

            try (InputStream inputStream = contentReader.getContentInputStream()) {
                long contentSize = contentReader.getSize();

                int startOffset = (fromOffset != null && fromOffset > 0) ? fromOffset : 0;
                int endOffset = getEndOffset(toOffset, contentSize, startOffset);

                inputStream.skip(startOffset);

                // Read only the requested range
                int rangeSize = endOffset - startOffset + 1;
                byte[] buffer = new byte[rangeSize];

                int totalRead = 0;
                int bytesRead;
                while (totalRead < rangeSize && (bytesRead = inputStream.read(buffer, totalRead, rangeSize - totalRead)) != -1) {
                    totalRead += bytesRead;
                }

                if (totalRead < rangeSize) {
                    byte[] result = new byte[totalRead];
                    System.arraycopy(buffer, 0, result, 0, totalRead);
                    return result;
                }

                return buffer;
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading content", e);
        }
    }

    /**
     * Calculates and validates the end offset for reading a portion of content.
     * It ensures the requested range is within the bounds of the content size.
     *
     * @param toOffset    The desired end offset. If null or negative, it defaults to the end of the content (`contentSize - 1`).
     * @param contentSize The total size of the content in bytes.
     * @param startOffset The starting offset from which reading begins.
     * @return The calculated and validated end offset.
     * @throws IllegalArgumentException if the start offset is outside the content bounds or greater than the end offset.
     */
    private static int getEndOffset(Integer toOffset, long contentSize, int startOffset) {
        int endOffset = (toOffset != null && toOffset >= 0) ? toOffset : (int) contentSize - 1;

        if (startOffset >= contentSize) {
            throw new IllegalArgumentException("fromOffset exceeds content size");
        }

        if (endOffset >= contentSize) {
            endOffset = (int) contentSize - 1;
        }

        if (startOffset > endOffset) {
            throw new IllegalArgumentException("fromOffset cannot be greater than toOffset");
        }
        return endOffset;
    }

}
