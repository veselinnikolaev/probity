package me.veselin.probity.bff.util;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;

public final class ConditionalGetSupport {

    private ConditionalGetSupport() {}

    /**
     * Returns true if the client's cached version is still fresh,
     * meaning the controller should return 304 Not Modified.
     */
    public static boolean isNotModified(Instant lastModified, String ifModifiedSince) {
        if (ifModifiedSince == null) return false;
        try {
            Instant clientTime = Instant.from(
                    DateTimeFormatter.RFC_1123_DATE_TIME.parse(ifModifiedSince)
            );
            return !lastModified.isAfter(clientTime);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Builds a 304 Not Modified response with Last-Modified header.
     */
    public static <T> ResponseEntity<T> notModified(Instant lastModified) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .lastModified(lastModified)
                .build();
    }

    /**
     * Wraps a body in 200 OK with Last-Modified and Cache-Control headers.
     * Uses no-cache + must-revalidate — correct for financial data.
     */
    public static <T> ResponseEntity<T> ok(T body, Instant lastModified) {
        return ResponseEntity.ok()
                .lastModified(lastModified)
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .body(body);
    }

    /**
     * Builds a 304 Not Modified response with ETag header.
     */
    public static <T> ResponseEntity<T> notModifiedETag(String etag) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(etag)
                .build();
    }

    /**
     * Wraps a body in 200 OK with ETag and immutable Cache-Control.
     * Use only for resources that never change after creation.
     */
    public static <T> ResponseEntity<T> okImmutable(T body, String etag) {
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).immutable())
                .body(body);
    }
}
