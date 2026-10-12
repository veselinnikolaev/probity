package me.veselin.probity.bff.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.auth.exception.SessionNotFoundException;
import me.veselin.probity.auth.exception.UnauthorizedException;
import me.veselin.probity.common.exception.ConflictException;
import me.veselin.probity.marketdata.exception.MarketDataException;
import me.veselin.probity.portfolio.exception.PortfolioNotFoundException;
import me.veselin.probity.portfolio.exception.PositionNotFoundException;
import me.veselin.probity.simulation.exception.EmptyPortfolioException;
import me.veselin.probity.simulation.exception.SimulationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 400 ───────────────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex,
                                              HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (first, second) -> first   // keep first message if field has multiple errors
                ));

        log.warn("Validation failed on path: {}, errors: {}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "error", "Validation failed",
                "fields", fieldErrors,
                "path", HtmlUtils.htmlEscape(request.getRequestURI()),
                "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleInvalidJson(HttpMessageNotReadableException ex,
                                                 HttpServletRequest request) {
        log.warn("Invalid JSON on path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "error", "Invalid JSON format",
                "path", HtmlUtils.htmlEscape(request.getRequestURI()),
                "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBadRequest(IllegalArgumentException ex,
                                              HttpServletRequest request) {
        log.warn("Bad request on path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "error", "Invalid request input",
                "path", HtmlUtils.htmlEscape(request.getRequestURI()),
                "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(EmptyPortfolioException.class)
    public ResponseEntity<?> handleEmptyPortfolio(EmptyPortfolioException ex,
                                                  HttpServletRequest request) {
        log.warn("Empty portfolio on path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "error", ex.getMessage(),
                "path", HtmlUtils.htmlEscape(request.getRequestURI()),
                "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                  HttpServletRequest request) {
        log.warn("Type mismatch on path: {}, parameter: {}, message: {}", request.getRequestURI(), ex.getName(), ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "error", "Invalid parameter format: " + ex.getName(),
                "path", HtmlUtils.htmlEscape(request.getRequestURI()),
                "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                        HttpServletRequest request) {
        log.warn("Unsupported media type on path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of(
                "status", 415,
                "error", "Unsupported media type",
                "path", HtmlUtils.htmlEscape(request.getRequestURI()),
                "timestamp", Instant.now()
        ));
    }

    // ── 401 ───────────────────────────────────────────────────────────────────

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class, UnauthorizedException.class})
    public ResponseEntity<?> handleUnauthorized(RuntimeException ex,
                                                HttpServletRequest request) {
        log.warn("Unauthorized access attempt on path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "status", 401,
                "error", ex.getMessage(),
                "path", HtmlUtils.htmlEscape(request.getRequestURI()),
                "timestamp", Instant.now()
        ));
    }

    // ── 403 ───────────────────────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleForbidden(Exception ex,
                                             HttpServletRequest request) {
        log.warn("Access denied on path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", 403,
                "error", "Access denied",
                "path", HtmlUtils.htmlEscape(request.getRequestURI()),
                "timestamp", Instant.now()
        ));
    }

    // ── 404 ───────────────────────────────────────────────────────────────────

    @ExceptionHandler({PortfolioNotFoundException.class, PositionNotFoundException.class, SimulationNotFoundException.class, SessionNotFoundException.class})
    public ResponseEntity<?> handleNotFound(RuntimeException ex,
                                            HttpServletRequest request) {
        log.warn("Resource not found on path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", 404,
                "error", ex.getMessage(),
                "path", HtmlUtils.htmlEscape(request.getRequestURI()),
                "timestamp", Instant.now()
        ));
    }

    // ── 409 ───────────────────────────────────────────────────────────────────

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<?> handleConflict(ConflictException ex,
                                            HttpServletRequest request) {
        log.warn("Conflict on path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", 409,
                "error", ex.getMessage(),
                "path", HtmlUtils.htmlEscape(request.getRequestURI()),
                "timestamp", Instant.now()
        ));
    }

    // ── 500 ───────────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex,
                                           HttpServletRequest request) {
        log.error("Unexpected error on path: {}, message: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.internalServerError().body(Map.of(
                "status", 500,
                "error", ex.getMessage(),
                "path", HtmlUtils.htmlEscape(request.getRequestURI()),
                "timestamp", Instant.now()
        ));
    }

    // ── 502 ───────────────────────────────────────────────────────────────────

    @ExceptionHandler(MarketDataException.class)
    public ResponseEntity<?> handleMarketData(MarketDataException ex,
                                              HttpServletRequest request) {
        log.error("Market data service error on path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "status", 502,
                "error", ex.getMessage(),
                "path", HtmlUtils.htmlEscape(request.getRequestURI()),
                "timestamp", Instant.now()
        ));
    }
}