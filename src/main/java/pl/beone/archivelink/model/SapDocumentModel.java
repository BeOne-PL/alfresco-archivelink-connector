package pl.beone.archivelink.model;

import org.alfresco.service.namespace.QName;

public interface SapDocumentModel {
    String SAP_ARCHIVELINK_NS = "http://www.beone.pl/sap-archivelink/documentmodel/1.0";

    QName SAP_DOCUMENT_TYPE = QName.createQName(SAP_ARCHIVELINK_NS, "sapDocument");

    QName SAP_COMPONENT_ASSOC = QName.createQName(SAP_ARCHIVELINK_NS, "componentList");
    QName SAP_ATTACHMENT_ASSOC = QName.createQName(SAP_ARCHIVELINK_NS, "attachmentList");

    QName PROP_SAP_DOCUMENT_ID = QName.createQName(SAP_ARCHIVELINK_NS, "docId");
    QName PROP_SAP_COMPONENT_ID = QName.createQName(SAP_ARCHIVELINK_NS, "compId");
    QName PROP_SAP_CONTENT_REP = QName.createQName(SAP_ARCHIVELINK_NS, "contRep");
    QName PROP_SAP_VERSION = QName.createQName(SAP_ARCHIVELINK_NS, "version");
    QName PROP_SAP_DOC_PROT = QName.createQName(SAP_ARCHIVELINK_NS, "docProt");
    QName PROP_SAP_IMPORT_DATE = QName.createQName(SAP_ARCHIVELINK_NS, "importDate");
    QName PROP_SAP_PROTOCOL_VERSION = QName.createQName(SAP_ARCHIVELINK_NS, "pVersion");
}
