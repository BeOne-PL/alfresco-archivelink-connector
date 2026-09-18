package pl.beone.archivelink.exception;

public class DuplicatedDocumentException extends RuntimeException {
    public DuplicatedDocumentException(String docId, String contRep) {
        super("Document " + docId + " in repository " + contRep + " is duplicated");
    }
}
