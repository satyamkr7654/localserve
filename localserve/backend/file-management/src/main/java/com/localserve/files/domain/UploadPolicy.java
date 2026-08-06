package com.localserve.files.domain;

import com.localserve.shared.error.DomainException;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class UploadPolicy {
    private static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> DOCUMENTS = Set.of("image/jpeg", "image/png", "application/pdf");
    private final Map<FilePurpose, Long> maximumBytes;

    public UploadPolicy(Map<FilePurpose, Long> maximumBytes) {
        this.maximumBytes = Map.copyOf(maximumBytes);
        for (FilePurpose purpose : FilePurpose.values()) {
            if (this.maximumBytes.getOrDefault(purpose, 0L) <= 0) throw new IllegalArgumentException("missing upload limit for " + purpose);
        }
    }

    public void requireAllowed(FilePurpose purpose, String declaredContentType, long sizeBytes, String originalName) {
        Objects.requireNonNull(purpose, "purpose");
        String contentType = Objects.requireNonNull(declaredContentType, "declaredContentType").toLowerCase();
        if (sizeBytes < 1 || sizeBytes > maximumBytes.get(purpose)) {
            throw new DomainException("FILE.SIZE_REJECTED", "File size is not allowed");
        }
        Set<String> allowed = purpose == FilePurpose.PROVIDER_IDENTITY || purpose == FilePurpose.PROVIDER_CERTIFICATE
                ? DOCUMENTS : IMAGES;
        if (!allowed.contains(contentType)) throw new DomainException("FILE.TYPE_REJECTED", "File type is not allowed");
        String safeName = Objects.requireNonNull(originalName, "originalName");
        if (safeName.length() > 160 || safeName.contains("/") || safeName.contains("\\") || safeName.indexOf('\0') >= 0) {
            throw new DomainException("FILE.NAME_REJECTED", "File name is not allowed");
        }
    }
}
