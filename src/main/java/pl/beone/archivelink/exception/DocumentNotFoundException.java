package pl.beone.archivelink.exception;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String docId, String contRep) {
        super("Document " + docId + " not found in content repository " + contRep);
    }
}
