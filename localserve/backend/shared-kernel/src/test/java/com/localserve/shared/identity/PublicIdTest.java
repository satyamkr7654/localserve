package com.localserve.shared.identity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicIdTest {
    @Test
    void generatesCanonicalUuidV7() {
        PublicId id = PublicId.generate();
        assertThat(id.value().version()).isEqualTo(7);
        assertThat(id.value().variant()).isEqualTo(2);
        assertThat(PublicId.parse(id.toString())).isEqualTo(id);
    }

    @Test
    void rejectsNonV7AndUppercaseText() {
        assertThatThrownBy(() -> new PublicId(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        String upper = PublicId.generate().toString().toUpperCase();
        assertThatThrownBy(() -> PublicId.parse(upper))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
