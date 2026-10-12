package me.veselin.probity.auth.enumeration;

import lombok.Getter;

@Getter
public enum Token {
    ACCESS("access_token"),
    REFRESH("refresh_token");

    private final String cookieName;

    Token(String cookieName) {
        this.cookieName = cookieName;
    }
}
