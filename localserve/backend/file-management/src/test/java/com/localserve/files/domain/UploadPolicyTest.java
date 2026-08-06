package com.localserve.files.domain;

import com.localserve.shared.error.DomainException;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadPolicyTest {
    @Test void blocksExecutableAndPathTraversalNames() {
        var limits = new EnumMap<FilePurpose, Long>(FilePurpose.class);
        for (var purpose : FilePurpose.values()) limits.put(purpose, 10_000L);
        var policy = new UploadPolicy(limits);
        assertThrows(DomainException.class, () -> policy.requireAllowed(FilePurpose.PROVIDER_IDENTITY,
                "application/x-msdownload", 100, "id.exe"));
        assertThrows(DomainException.class, () -> policy.requireAllowed(FilePurpose.PROFILE_IMAGE,
                "image/png", 100, "../avatar.png"));
    }
}
