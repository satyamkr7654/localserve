package com.localserve.identity.password;

public interface BreachedPasswordChecker {
    boolean isKnownBreached(char[] password);
}
