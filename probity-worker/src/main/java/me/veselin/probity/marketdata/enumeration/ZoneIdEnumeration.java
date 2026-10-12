package me.veselin.probity.marketdata.enumeration;

import lombok.Getter;

@Getter
public enum ZoneIdEnumeration {
    NEW_YORK("America/New_York"),
    LONDON("Europe/London"),
    TOKYO("Asia/Tokyo"),
    PARIS("Europe/Paris");


    private final String zoneId;

    ZoneIdEnumeration(String zoneId) {
        this.zoneId = zoneId;
    }
}