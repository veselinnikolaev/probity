package me.veselin.probity.common.domain.event;

import java.util.regex.Pattern;

/** Strict resolution of the dispatch-generation carriers used by Kafka messages. */
public final class DispatchGeneration {

  private static final Pattern VALID = Pattern.compile("^[1-9][0-9]{0,9}$");

  private DispatchGeneration() {}

  public enum Source {
    HEADER,
    ENVELOPE,
    LEGACY_DEFAULT,
    FUTURE,
    ABSENT,
    MALFORMED,
    CORRUPT
  }

  public record Resolution(Integer value, Source source, String rawHeader, Integer envelopeValue) {
    public boolean isValid() {
      return value != null
          && (source == Source.HEADER
              || source == Source.ENVELOPE
              || source == Source.LEGACY_DEFAULT);
    }
  }

  public static Resolution resolve(
      String headerValue, Integer envelopeValue, boolean legacyDefaultEnabled) {
    Integer headerGeneration = parseHeader(headerValue);
    boolean headerPresent = headerValue != null;
    boolean envelopePresent = envelopeValue != null;

    if (headerPresent && headerGeneration == null) {
      return new Resolution(null, Source.MALFORMED, headerValue, envelopeValue);
    }
    if (envelopePresent && (envelopeValue < 1)) {
      return new Resolution(null, Source.MALFORMED, headerValue, envelopeValue);
    }
    if (headerGeneration != null && envelopeValue != null
        && !headerGeneration.equals(envelopeValue)) {
      return new Resolution(null, Source.CORRUPT, headerValue, envelopeValue);
    }
    if (headerGeneration != null) {
      return new Resolution(headerGeneration, Source.HEADER, headerValue, envelopeValue);
    }
    if (envelopeValue != null) {
      return new Resolution(envelopeValue, Source.ENVELOPE, headerValue, envelopeValue);
    }
    if (legacyDefaultEnabled) {
      return new Resolution(1, Source.LEGACY_DEFAULT, null, null);
    }
    return new Resolution(null, Source.ABSENT, null, null);
  }

  private static Integer parseHeader(String value) {
    if (value == null || !VALID.matcher(value).matches()) {
      return null;
    }
    try {
      long parsed = Long.parseLong(value);
      return parsed <= Integer.MAX_VALUE ? (int) parsed : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }
}
