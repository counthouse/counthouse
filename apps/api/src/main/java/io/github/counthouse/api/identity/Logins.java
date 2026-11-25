package io.github.counthouse.api.identity;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Finds the staff member a token signs in as, in the tenant of the current scope. */
@Service
class Logins {

    private final LoginRows rows;

    Logins(LoginRows rows) {
        this.rows = rows;
    }

    @Transactional(readOnly = true)
    Optional<Actor> findActivePerson(String issuer, String subject) {
        return rows.findActivePerson(issuer, subject);
    }
}
