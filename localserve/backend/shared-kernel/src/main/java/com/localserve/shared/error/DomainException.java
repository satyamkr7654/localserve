package com.localserve.shared.error;

import java.util.Map;
import java.util.Objects;

public class DomainException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final Map<String, Object> safeMetadata;

    public DomainException(String code, String safeMessage) {
        this(code, safeMessage, Map.of());
    }

    public DomainException(String code, String safeMessage, Map<String, Object> safeMetadata) {
        super(Objects.requireNonNull(safeMessage, "safeMessage"));
        if (code == null || !code.matches("[A-Z]+(?:[_.][A-Z0-9]+)+")) {
            throw new IllegalArgumentException("domain error code has an invalid format");
        }
        this.code = code;
        this.safeMetadata = Map.copyOf(Objects.requireNonNull(safeMetadata, "safeMetadata"));
    }

    public String code() {
        return code;
    }

    public Map<String, Object> safeMetadata() {
        return safeMetadata;
    }
}
