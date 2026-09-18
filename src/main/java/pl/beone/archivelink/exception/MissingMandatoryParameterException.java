package pl.beone.archivelink.exception;

public class MissingMandatoryParameterException extends RuntimeException {
    public MissingMandatoryParameterException(String mandatoryParameter) {
        super("Missing mandatory parameter: " + mandatoryParameter);
    }
}
