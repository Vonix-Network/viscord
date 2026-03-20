package network.vonix.viscord.discord;

/**
 * Exception thrown when advancement data extraction from Discord embeds fails.
 * This exception is used to handle various error conditions during the extraction
 * process, such as missing fields, malformed data, or validation failures.
 */
public class ExtractionException extends Exception {

    /**
     * Creates a new ExtractionException with the specified message.
     */
    public ExtractionException(String message) {
        super(message);
    }

    /**
     * Creates a new ExtractionException with the specified message and cause.
     */
    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
