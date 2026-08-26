package dev.mintychochip.territory.influence;

/**
 * Declare/cancel outcome with a human-readable message.
 *
 * @param status outcome status
 * @param message human-readable outcome message
 */
public record DeclareResult(DeclareStatus status, String message) {

    /** Validates and normalizes the result components. */
    public DeclareResult {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        message = message == null ? "" : message;
    }

    /**
     * Creates a successful or otherwise normal declaration result.
     *
     * @param status outcome status
     * @param message human-readable outcome message
     * @return a declaration result
     */
    public static DeclareResult ok(DeclareStatus status, String message) {
        return new DeclareResult(status, message);
    }

    /**
     * Creates an error declaration result.
     *
     * @param status error status
     * @param message human-readable error message
     * @return an error declaration result
     */
    public static DeclareResult error(DeclareStatus status, String message) {
        return new DeclareResult(status, message);
    }

    /**
     * Tests whether this result represents a successful operation.
     *
     * @return whether the status is declared or cancelled
     */
    public boolean isSuccess() {
        return status == DeclareStatus.DECLARED || status == DeclareStatus.CANCELLED;
    }
}
