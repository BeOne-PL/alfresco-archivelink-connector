package pl.beone.archivelink.exception;

public class ComponentNotFoundException extends RuntimeException {
    public ComponentNotFoundException(String docId, String compId) {
        super("Component " + compId + " not found in document " + docId);
    }
}
