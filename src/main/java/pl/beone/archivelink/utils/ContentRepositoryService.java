package pl.beone.archivelink.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespacePrefixResolver;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import pl.beone.archivelink.exception.DocumentNotFoundException;
import pl.beone.archivelink.exception.DuplicatedDocumentException;
import pl.beone.archivelink.exception.MissingNodeException;
import pl.beone.archivelink.model.SapDocumentModel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static pl.beone.archivelink.model.SapDocumentModel.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class ContentRepositoryService {

    private static final String SAP_ARCHIVELINK_FOLDER = "SAP_ArchiveLink";

    private final ArchiveLinkFileUtil fileUtil;
    @Qualifier("NodeService") private final NodeService nodeService;
    @Qualifier("SearchService") private final SearchService searchService;
    @Qualifier("namespaceService") private final NamespacePrefixResolver namespaceService;

    /**
     * Gets the company home folder
     *
     * @return Reference to a company home folder node
     * @throws MissingNodeException When the company home folder is not found
     */
    public NodeRef getCompanyHome() throws MissingNodeException {
//        NodeRef rootNodeRef = nodeService.getRootNode(new StoreRef("workspace://SpacesStore"));
//        return fileUtil.getFolder(rootNodeRef, "Company Home")
//                .orElseThrow(() -> new MissingNodeException("Unable to find company home"));
        StoreRef storeRef = new StoreRef("workspace://SpacesStore");
        String xpathQuery = "/app:company_home";

        ResultSet resultSet = null;
        try {
            resultSet = searchService.query(storeRef, SearchService.LANGUAGE_XPATH, xpathQuery);
            if (resultSet.length() == 0) {
                throw new MissingNodeException("Unable to find company home");
            }

            return resultSet.getNodeRef(0);
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
    }

    /**
     * Returns a SAP content repository folder. If it cannot find one, creates it.
     *
     * @param contRep Content repository name
     * @return Reference to a repository folder
     * @throws MissingNodeException When the company home folder is not found
     */
    public NodeRef getOrCreateContentRepositoryFolder(String contRep) throws MissingNodeException {
        NodeRef companyHome = getCompanyHome();

        NodeRef sapFolder = nodeService.getChildByName(companyHome, ContentModel.ASSOC_CONTAINS, SAP_ARCHIVELINK_FOLDER);
        if (sapFolder == null) {
            String folderTitle = "SAP ArchiveLink Content Repository";
            QName assocQName = QName.createQName(ContentModel.USER_MODEL_URI, SAP_ARCHIVELINK_FOLDER);
            sapFolder = fileUtil.createFolder(companyHome, SAP_ARCHIVELINK_FOLDER, folderTitle, assocQName);
        }

        NodeRef contRepFolder = nodeService.getChildByName(sapFolder, ContentModel.ASSOC_CONTAINS, contRep);
        if (contRepFolder == null) {
            String folderTitle = "Content Repository: " + contRep;
            QName assocQName = QName.createQName(ContentModel.USER_MODEL_URI, contRep);
            contRepFolder = fileUtil.createFolder(sapFolder, contRep, folderTitle, assocQName);
        }

        return contRepFolder;
    }

    /**
     * Returns a list of all content repository folders inside a dedicated SAP folder (creates one if it doesn't exist)
     *
     * @param contRep Nullable specific content repository name
     * @return A list of all content repository folders (or just one if contRep is not null)
     */
    public List<NodeRef> getContentRepositoryFolders(String contRep) {
        var companyHome = getCompanyHome();

        var sapFolder = nodeService.getChildByName(companyHome, ContentModel.ASSOC_CONTAINS, SAP_ARCHIVELINK_FOLDER);
        if (sapFolder == null) {
            var props = new HashMap<QName, Serializable>();
            props.put(ContentModel.PROP_NAME, SAP_ARCHIVELINK_FOLDER);
            props.put(ContentModel.PROP_TITLE, "SAP ArchiveLink Content Repository");
            sapFolder = nodeService.createNode(
                    companyHome,
                    ContentModel.ASSOC_CONTAINS,
                    QName.createQName(ContentModel.USER_MODEL_URI, SAP_ARCHIVELINK_FOLDER),
                    ContentModel.TYPE_FOLDER,
                    props
            ).getChildRef();
        }

        if (contRep == null) {
            return nodeService.getChildAssocs(sapFolder)
                    .stream()
                    .map(ChildAssociationRef::getChildRef)
                    .collect(Collectors.toList());
        }

        var contRepFolder = nodeService.getChildByName(sapFolder, ContentModel.ASSOC_CONTAINS, contRep);
        if (contRepFolder == null) {
            String folderTitle = "Content Repository: " + contRep;
            QName assocQName = QName.createQName(ContentModel.USER_MODEL_URI, contRep);
            contRepFolder = fileUtil.createFolder(sapFolder, contRep, folderTitle, assocQName);
        }

        return List.of(contRepFolder);
    }

    /**
     * Returns a day folder inside a YEAR/MONTH/DAY structure inside a content repository to put files into.
     * If any of the folders doesn't exist, creates it.
     *
     * @param contRep Content repository where the file structure should be
     * @return Reference to a DAY folder node
     */
    public NodeRef getOrCreateFolderStructure(NodeRef contRep) {
        var currentDate = LocalDateTime.now(ZoneOffset.UTC);
        String yearStr = String.valueOf(currentDate.getYear());
        String monthStr = currentDate.getMonth().toString();
        String dayStr = String.valueOf(currentDate.getDayOfMonth());

        var yearFolder = fileUtil.getFolder(contRep, yearStr)
                .orElseGet(() -> fileUtil.createFolder(contRep, yearStr, yearStr, QName.resolveToQName(namespaceService, "cm:" + yearStr)));

        var monthFolder = fileUtil.getFolder(yearFolder, monthStr)
                .orElseGet(() -> fileUtil.createFolder(yearFolder, monthStr, monthStr, QName.resolveToQName(namespaceService, "cm:" + monthStr)));

        return fileUtil.getFolder(monthFolder, dayStr)
                .orElseGet(() -> fileUtil.createFolder(monthFolder, dayStr, dayStr, QName.resolveToQName(namespaceService, "cm:" + dayStr)));
    }

    /**
     * Gets a document by docID and content repository name
     *
     * @param docId   Document ID
     * @param contRep Content repository name
     * @return Reference to a document node
     * @throws DocumentNotFoundException When the document doesn't exist
     */
    public NodeRef getDocument(String docId, String contRep) throws DocumentNotFoundException {
        var documentNodes = searchService.query(
                StoreRef.STORE_REF_WORKSPACE_SPACESSTORE,
                SearchService.LANGUAGE_LUCENE,
                "TYPE:\"sapDoc:sapDocument\" AND @sapDoc\\:docId:" + docId + " AND " + "@sapDoc\\:contRep:" + contRep
        ).getNodeRefs();

        if (documentNodes.isEmpty())
            throw new DocumentNotFoundException(docId, contRep);

        return documentNodes.get(0);
    }

    /**
     * Gets a component belonging to a document by ID
     *
     * @param document Parent document node
     * @param compId   Componend ID
     * @return Component node reference, or an empty Optional if it couldn't be found
     */
    public Optional<NodeRef> getComponent(NodeRef document, String compId) {
        return getComponents(document)
                .stream()
                .filter(comp -> {
                    String nodeCompId = getComponentId(comp);
                    return compId.equals(nodeCompId);
                })
                .findFirst();
    }

    /**
     * @param component Component node
     * @return ID of a component
     */
    public String getComponentId(NodeRef component) {
        return (String) nodeService.getProperty(component, SapDocumentModel.PROP_SAP_COMPONENT_ID);
    }

    /**
     * Returns component nodes of a document
     *
     * @param document Parent document
     * @return List of component nodes
     */
    public List<NodeRef> getComponents(NodeRef document) {
        var componentAssocs = nodeService.getTargetAssocs(document, SapDocumentModel.SAP_COMPONENT_ASSOC);
        return componentAssocs
                .stream()
                .map(AssociationRef::getTargetRef)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a component exists in a document
     *
     * @param document Document node
     * @param compId   Component id
     * @return True if component was found, otherwise false
     */
    public boolean componentExists(NodeRef document, String compId) {
        return getComponent(document, compId).isPresent();
    }

    /**
     * Creates a document in a target folder with given props
     *
     * @param targetFolder Target folder where the document will be created
     * @param docId        Document ID
     * @param contRep      Content repository name
     * @param docProt      Document protection (can be null, in that case the information isn't included)
     * @param pVersion     pVersion (can be null, in that case the information isn't included)
     * @return Reference to a document node
     */
    public NodeRef createDocument(NodeRef targetFolder, String docId, String contRep, String docProt, String pVersion) {
        var documentProps = new HashMap<QName, Serializable>();

        documentProps.put(ContentModel.PROP_NAME, docId);
//        documentProps.put(ContentModel.PROP_CONTENT, "SAP Document: " + docId);

        documentProps.put(PROP_SAP_CONTENT_REP, contRep);
        documentProps.put(PROP_SAP_DOCUMENT_ID, docId);
        documentProps.put(PROP_SAP_IMPORT_DATE, new Date());

        if (pVersion != null) {
            documentProps.put(PROP_SAP_PROTOCOL_VERSION, pVersion);
        }
        if (docProt != null) {
            documentProps.put(PROP_SAP_DOC_PROT, docProt);
        }

        var now = new Date();
        String currentUser = AuthenticationUtil.getFullyAuthenticatedUser();
        documentProps.put(ContentModel.PROP_CREATED, now);
        documentProps.put(ContentModel.PROP_MODIFIED, now);
        documentProps.put(ContentModel.PROP_CREATOR, currentUser);
        documentProps.put(ContentModel.PROP_MODIFIER, currentUser);

        var documentNode = nodeService.createNode(
                targetFolder,
                ContentModel.ASSOC_CONTAINS,
                QName.resolveToQName(namespaceService, "cm:" + docId),
                SAP_DOCUMENT_TYPE,
                documentProps
        ).getChildRef();
        nodeService.addAspect(documentNode, ContentModel.ASPECT_AUDITABLE, null);

        return documentNode;
    }

    /**
     * Creates a component in a target document with given props
     *
     * @param documentNode  Parent document node
     * @param compId        Component ID
     * @param mimeType      MIME type of component content
     * @param contentStream Content stream of component
     * @param contRep       Content repository name
     * @param docId         Document ID
     * @param pVersion      pVersion (can be null, in that case the information isn't included)
     * @throws IOException If an I/O error occurs when reading component content
     */
    public void createDocumentComponent(
            NodeRef documentNode,
            String compId,
            String mimeType,
            InputStream contentStream,
            String contRep,
            String docId,
            String pVersion
    ) throws IOException {
        var componentProps = new HashMap<QName, Serializable>();
        componentProps.put(ContentModel.PROP_NAME, compId);
        componentProps.put(ContentModel.PROP_TITLE, "SAP Component: " + compId);

        componentProps.put(PROP_SAP_CONTENT_REP, contRep);
        componentProps.put(PROP_SAP_DOCUMENT_ID, docId);
        componentProps.put(PROP_SAP_COMPONENT_ID, compId);
        componentProps.put(PROP_SAP_IMPORT_DATE, new Date());

        if (pVersion != null) {
            componentProps.put(PROP_SAP_PROTOCOL_VERSION, pVersion);
        }

        var componentContent = contentStream.readAllBytes();
        componentProps.put(ContentModel.PROP_SIZE_CURRENT, componentContent.length);

        NodeRef componentNode = nodeService.createNode(
                documentNode,
                SAP_COMPONENT_ASSOC,
                QName.resolveToQName(namespaceService, "cm:" + compId),
                ContentModel.TYPE_CONTENT,
                componentProps
        ).getChildRef();

        nodeService.createAssociation(documentNode, componentNode, SAP_COMPONENT_ASSOC);

        fileUtil.writeContentToNode(mimeType, componentNode, new ByteArrayInputStream(componentContent));

        log.debug("Content written to component node: {}", compId);
    }

    /**
     * Checks whether a document already exists at a given content repository
     *
     * @param docId   Document ID
     * @param contRep Content repository name
     * @throws DuplicatedDocumentException When the document already exists
     */
    public void validateDuplicate(String docId, String contRep) throws DuplicatedDocumentException {
        var documentNodes = searchService.query(
                StoreRef.STORE_REF_WORKSPACE_SPACESSTORE,
                SearchService.LANGUAGE_LUCENE,
                "@sapDoc\\:docId:" + docId + " AND " + "@sapDoc\\:contRep:" + contRep
        ).getNodeRefs();

        if (!documentNodes.isEmpty())
            throw new DuplicatedDocumentException(docId, contRep);
    }

}
