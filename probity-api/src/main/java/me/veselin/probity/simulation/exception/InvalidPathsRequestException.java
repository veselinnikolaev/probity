package me.veselin.probity.simulation.exception;

/**
 * Raised when a caller asks the sampled-paths endpoint
 * ({@code GET /api/v1/simulations/{id}/paths}) for a sample the endpoint will not serve —
 * an {@code n} outside {@code [1, max]}, or an <em>explicit</em> {@code n} above the number
 * of stored paths (Q9b API split, docs/plans/Q9_RESULT_PAYLOAD_STORAGE.md §10). An omitted
 * {@code n} is the configured default and is served capped to what exists, so it never
 * reaches this exception.
 *
 * <p>This is a request-shape failure, not a client error the caller can retry around: the
 * bounds are deliberately rejected rather than clamped so a client cannot ask for a body
 * larger than the configured ceiling. Mapped to HTTP 400 by the BFF exception handler, with
 * the message preserved so the caller learns which bound it crossed.
 */
public class InvalidPathsRequestException extends RuntimeException {
    public InvalidPathsRequestException(String message) {
        super(message);
    }
}
