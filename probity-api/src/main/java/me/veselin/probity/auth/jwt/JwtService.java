package me.veselin.probity.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import me.veselin.probity.auth.enumeration.Token;
import me.veselin.probity.auth.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/** Issues and validates JWT tokens and manages refresh/blacklist state in Redis. */
public class JwtService {
  private final RedisTemplate<String, String> redisTemplate;

  @Value("${probity.jwt.secret}")
  private String secret;

  private SecretKey key;

  @Value("${probity.jwt.access.expiration.seconds}")
  private long accessExpirationTime;

  @Value("${probity.jwt.refresh.expiration.seconds}")
  private long refreshExpirationTime;

  @Value("${probity.jwt.issuer}")
  private String issuer;

  @Value("${probity.jwt.refresh-prefix}")
  private String refreshPrefix;

  @Value("${probity.jwt.blacklist-prefix}")
  private String blacklistedPrefix;

  @Value("${probity.jwt.session-prefix}")
  private String sessionPrefix;

  @PostConstruct
  /** Initializes the signing key from configuration and enforces minimum secret strength. */
  public void setKey() {
    if (secret.length() < 32) {
      throw new IllegalArgumentException("JWT secret must be at least 32 characters");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes());
  }

  /** Creates a short-lived access token with caller-supplied claims. */
  public String generateAccessJwt(String subject, Map<String, Object> claims) {
    long expiry = System.currentTimeMillis() + accessExpirationTime * 1000;

    return constructJwt(subject, claims, expiry, Token.ACCESS);
  }

  /** Creates a long-lived refresh token with caller-supplied claims. */
  public String generateRefreshJwt(String subject, Map<String, Object> claims) {
    long expiry = System.currentTimeMillis() + refreshExpirationTime * 1000;

    return constructJwt(subject, claims, expiry, Token.REFRESH);
  }

  /** Parses and validates JWT claims, converting parsing failures to unauthorized errors. */
  public Claims extractClaims(String token) throws UnauthorizedException {
    try {
      return Jwts.parser()
          .verifyWith(key)
          .requireIssuer(issuer)
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (ExpiredJwtException e) {
      throw new UnauthorizedException("Token expired");
    } catch (Exception e) {
      throw new UnauthorizedException("Invalid token");
    }
  }

  /**
   * Writes a session record to Redis when a user logs in. Key: session:{userId}:{jti} Value:
   * "{jti}|{deviceHint}|{ipAddress}|{issuedAtIso}" TTL: matches the refresh token lifetime so
   * sessions auto-expire.
   *
   * <p>Called by UserCommandService#login after generating tokens.
   */
  public void recordSession(
      UUID userId, String jti, String deviceHint, String ipAddress, String issuedAt) {
    String key = sessionKey(userId, jti);
    String value = jti + "|" + deviceHint + "|" + ipAddress + "|" + issuedAt;
    redisTemplate.opsForValue().set(key, value, refreshExpirationTime, TimeUnit.SECONDS);
  }

  /**
   * Returns all Redis keys matching a glob pattern (e.g. "session:{userId}:*"). Uses Redis SCAN so
   * large keyspaces do not block Redis with KEYS.
   */
  public Set<String> scanSessionKeys(String pattern) {
    return redisTemplate.execute(
        (RedisCallback<Set<String>>)
            connection -> {
              Set<String> keys = new HashSet<>();
              ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
              try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                  keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
              }
              return keys;
            });
  }

  /** Reads the raw session payload string for a given Redis key. */
  public String getRawSessionPayload(String key) {
    return redisTemplate.opsForValue().get(key);
  }

  /** Deletes a session key from Redis. Returns true if the key existed. */
  public boolean deleteSessionKey(String key) {
    return redisTemplate.delete(key);
  }

  public String extractUsername(String token) {
    return extractClaims(token).getSubject();
  }

  public String extractJti(String token) {
    return extractClaims(token).getId();
  }

  public boolean isTokenValid(String token, String username) {
    Claims claims = extractClaims(token);
    boolean notExpired = claims.getExpiration().after(new Date(System.currentTimeMillis()));
    return claims.getSubject().equals(username) && notExpired;
  }

  public boolean isTokenType(String token, String expectedType) {
    return expectedType.equals(extractClaims(token).get("token_type", String.class));
  }

  public void blacklistToken(String token) {
    if (!isTokenType(token, Token.ACCESS.getType())) {
      throw new UnauthorizedException("Invalid access token");
    }
    Claims claims = extractClaims(token);
    String jti = claims.getId();
    Duration ttl = Duration.between(Instant.now(), claims.getExpiration().toInstant());
    if (!ttl.isNegative() && !ttl.isZero()) {
      redisTemplate.opsForValue().set(blacklistKey(jti), "1", ttl);
    }
  }

  public boolean isBlacklisted(String jti) {
    return redisTemplate.hasKey(blacklistKey(jti));
  }

  public void saveRefreshToken(String refreshToken) {
    if (!isTokenType(refreshToken, Token.REFRESH.getType())) {
      throw new UnauthorizedException("Invalid refresh token");
    }
    redisTemplate
        .opsForValue()
        .set(
            refreshKey(extractJti(refreshToken)),
            refreshToken,
            refreshExpirationTime,
            TimeUnit.SECONDS);
  }

  public String getRefreshTokenByJti(String jti) {
    return redisTemplate.opsForValue().get(refreshKey(jti));
  }

  public void deleteRefreshTokenByJti(String jti) {
    redisTemplate.delete(refreshKey(jti));
  }

  public void deleteSession(UUID userId, String jti) {
    redisTemplate.delete(sessionKey(userId, jti));
  }

  public String sessionKey(UUID userId, String jti) {
    return sessionPrefix + userId + ":" + jti;
  }

  private String constructJwt(
      String subject, Map<String, Object> claims, long expiration, Token tokenType) {
    Map<String, Object> tokenClaims = new java.util.HashMap<>(claims);
    tokenClaims.put("token_type", tokenType.getType());
    return Jwts.builder()
        .id(UUID.randomUUID().toString())
        .issuer(issuer)
        .subject(subject)
        .claims(tokenClaims)
        .issuedAt(new Date(System.currentTimeMillis()))
        .expiration(new Date(expiration))
        .signWith(key)
        .compact();
  }

  private String blacklistKey(String jti) {
    return blacklistedPrefix + jti;
  }

  private String refreshKey(String jti) {
    return refreshPrefix + jti;
  }
}
