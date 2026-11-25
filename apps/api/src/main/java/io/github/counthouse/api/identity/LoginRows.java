package io.github.counthouse.api.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface LoginRows extends Repository<LoginRow, UUID> {

    @Query("""
            select new io.github.counthouse.api.identity.Actor(staffMember.id, staffMember.role)
            from LoginRow login join login.staffMember staffMember
            where login.issuer = :issuer and login.subject = :subject and login.kind = 'PERSON'
              and staffMember.active
            """)
    Optional<Actor> findActivePerson(String issuer, String subject);
}
