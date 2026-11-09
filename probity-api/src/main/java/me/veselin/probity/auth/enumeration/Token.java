package me.veselin.probity.auth.enumeration;

import lombok.Getter;

@Getter
public enum Token {
  ACCESS("access_token", "access"),
  REFRESH("refresh_token", "refresh");

  private final String cookieName;
  private final String type;

  Token(String cookieName, String type) {
    this.cookieName = cookieName;
    this.type = type;
  }
}
