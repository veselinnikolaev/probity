package me.veselin.probity.simulation.exception;

public class InsufficientMarketDataException extends RuntimeException {
    public InsufficientMarketDataException(String message) {
        super(message);
    }

    public InsufficientMarketDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
