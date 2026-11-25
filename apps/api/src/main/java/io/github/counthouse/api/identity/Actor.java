package io.github.counthouse.api.identity;

import java.io.Serializable;
import java.util.UUID;

/** The staff member a request acts for, read from the database on every request. */
public record Actor(UUID staffMemberId, Role role) implements Serializable {

    public enum Role {
        STAFF,
        ADMIN
    }
}
