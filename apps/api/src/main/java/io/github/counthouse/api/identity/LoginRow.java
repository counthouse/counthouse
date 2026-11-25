package io.github.counthouse.api.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "logins")
@Immutable
class LoginRow {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    private String issuer;

    private String subject;

    private String kind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_member_id")
    private StaffMemberRow staffMember;

    protected LoginRow() {}
}
