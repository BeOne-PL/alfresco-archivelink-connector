package pl.beone.archivelink.model;

import org.alfresco.service.namespace.QName;

public interface SapCertModel {
    String SAP_ARCHIVELINK_CERT_NS = "http://www.beone.pl/sap-archivelink/certmodel/1.0";

    QName ASPECT_ACCEPTED = QName.createQName(SAP_ARCHIVELINK_CERT_NS, "certAspect");
    QName PROP_IS_ACCEPTED = QName.createQName(SAP_ARCHIVELINK_CERT_NS, "isAccepted");
}
