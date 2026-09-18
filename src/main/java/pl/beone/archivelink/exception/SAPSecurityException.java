package pl.beone.archivelink.exception;

public class SAPSecurityException extends RuntimeException {

    public SAPSecurityException(String message) {
        super(message);
    }

    public static SAPSecurityException invalidSignature() {
        return new SAPSecurityException("Invalid secKey");
    }

    public static SAPSecurityException signatureVerificationFailed() {
        return new SAPSecurityException("Error while validating secKey");
    }

    public static SAPSecurityException insufficientAccess(String requiredAccess, String providedAccess) {
        return new SAPSecurityException("No required access: " + requiredAccess + ". Provided access: " + providedAccess);
    }

    public static SAPSecurityException missingSecKey() {
        return new SAPSecurityException("Missing secKey");
    }

    public static SAPSecurityException missingAccessMode() {
        return new SAPSecurityException("Missing accessMode");
    }

    public static SAPSecurityException missingAuthId() {
        return new SAPSecurityException("Missing authId");
    }

    public static SAPSecurityException missingExpiration() {
        return new SAPSecurityException("Missing expiration");
    }

    public static SAPSecurityException missingContentRepository() {
        return new SAPSecurityException("Missing contRep");
    }

    public static SAPSecurityException invalidExpiration(String expiration) {
        return new SAPSecurityException("Invalid expiration: " + expiration);
    }

    public static SAPSecurityException urlExpired(String expiration) {
        return new SAPSecurityException("Request expired. Expiration: " + expiration);
    }

    public static SAPSecurityException missingPublicKey() {
        return new SAPSecurityException("Unable to verify signature. No public key found.");
    }

    public static SAPSecurityException publicKeyNotAccepted() {
        return new SAPSecurityException("Public key signature hasn't been accepted yet.");
    }
}
