package com.localserve.shared.security;

import com.localserve.shared.identity.PublicId;

import java.util.Objects;

public record Actor(ActorType type, PublicId id) {
    public Actor {
        Objects.requireNonNull(type, "type");
        if (type == ActorType.SYSTEM && id != null) {
            throw new IllegalArgumentException("system actor must not have a public user identifier");
        }
        if (type != ActorType.SYSTEM && id == null) {
            throw new IllegalArgumentException("non-system actor requires a public identifier");
        }
    }

    public static Actor system() {
        return new Actor(ActorType.SYSTEM, null);
    }

    public static Actor customer(PublicId id) {
        return new Actor(ActorType.CUSTOMER, id);
    }

    public static Actor provider(PublicId id) {
        return new Actor(ActorType.PROVIDER, id);
    }

    public static Actor admin(PublicId id) {
        return new Actor(ActorType.ADMIN, id);
    }
}
