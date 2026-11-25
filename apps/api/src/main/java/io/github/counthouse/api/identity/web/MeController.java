package io.github.counthouse.api.identity.web;

import io.github.counthouse.api.identity.Actor;
import io.github.counthouse.api.identity.CurrentActor;
import io.github.counthouse.api.tenancy.TenantSettings;
import io.github.counthouse.api.tenancy.Tenants;
import io.github.counthouse.contract.MeApi;
import io.github.counthouse.contract.model.Me;
import io.github.counthouse.contract.model.Role;
import io.github.counthouse.contract.model.Tenant;
import io.github.counthouse.contract.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController implements MeApi {

    private final Tenants tenants;

    MeController(Tenants tenants) {
        this.tenants = tenants;
    }

    @Override
    public ResponseEntity<Me> getMe() {
        Actor actor = CurrentActor.get();
        TenantSettings tenant = tenants.current();
        return ResponseEntity.ok(new Me(
                new User(actor.staffMemberId(), Role.valueOf(actor.role().name())),
                new Tenant(tenant.id(), tenant.name(), tenant.timeZone(), tenant.locale())));
    }
}
