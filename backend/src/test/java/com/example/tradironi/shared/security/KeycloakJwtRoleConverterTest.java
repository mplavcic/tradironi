package com.example.tradironi.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtRoleConverterTest {

    private final KeycloakJwtRoleConverter converter = new KeycloakJwtRoleConverter();

    private Jwt jwtWithRealmAccess(Object realmAccess) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("sub", "123e4567-e89b-42d3-a456-426614174000");
        if (realmAccess != null) {
            builder.claim("realm_access", realmAccess);
        }
        return builder.build();
    }

    @Test
    void convertsRolesToGrantedAuthoritiesWithRolePrefix() {
        Jwt jwt = jwtWithRealmAccess(Map.of("roles", List.of("ADMIN", "member")));

        List<String> authorities = converter.convert(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).containsExactly("ROLE_ADMIN", "ROLE_MEMBER");
    }

    @Test
    void returnsEmptyWhenRealmAccessMissing() {
        Jwt jwt = jwtWithRealmAccess(null);

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void returnsEmptyWhenRolesKeyMissing() {
        Jwt jwt = jwtWithRealmAccess(Map.of("other", "value"));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void returnsEmptyWhenRolesEmpty() {
        Jwt jwt = jwtWithRealmAccess(Map.of("roles", List.of()));

        assertThat(converter.convert(jwt)).isEmpty();
    }
}