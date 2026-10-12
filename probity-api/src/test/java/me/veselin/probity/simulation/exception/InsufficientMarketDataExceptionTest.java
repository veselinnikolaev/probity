package me.veselin.probity.simulation.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InsufficientMarketDataExceptionTest {

    @Test
    void constructorStoresMessage() {
        InsufficientMarketDataException ex = new InsufficientMarketDataException("msg");
        assertEquals("msg", ex.getMessage());
    }

    @Test
    void constructorStoresMessageAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("c");
        InsufficientMarketDataException ex = new InsufficientMarketDataException("msg", cause);
        assertEquals("msg", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void isRuntimeException() {
        assertThrows(InsufficientMarketDataException.class, () -> {
            throw new InsufficientMarketDataException("boom");
        });
    }
}