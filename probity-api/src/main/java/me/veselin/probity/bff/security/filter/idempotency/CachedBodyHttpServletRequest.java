package me.veselin.probity.bff.security.filter.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;

final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
  private final byte[] body;

  CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
    super(request);
    this.body = body;
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream input = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override public int read() { return input.read(); }
      @Override public boolean isFinished() { return input.available() == 0; }
      @Override public boolean isReady() { return true; }
      @Override public void setReadListener(ReadListener listener) {}
    };
  }

  @Override
  public java.io.BufferedReader getReader() throws IOException {
    String encoding = getCharacterEncoding();
    return new java.io.BufferedReader(
        new java.io.InputStreamReader(
            getInputStream(),
            encoding == null
                ? java.nio.charset.StandardCharsets.UTF_8
                : java.nio.charset.Charset.forName(encoding)));
  }
}
