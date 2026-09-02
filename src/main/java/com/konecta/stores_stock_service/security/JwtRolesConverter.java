package com.konecta.stores_stock_service.security;

import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * The security service issues a single "roles" claim already in ROLE_&lt;CODE&gt;
 * form (e.g. "ROLE_MERCHANT"), not the space-separated "scope" shape Spring's
 * default converter expects.
 */
@Component
public class JwtRolesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        String roles = jwt.getClaimAsString("roles");
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(roles.split("[,\\s]+"))
                .filter(r -> !r.isBlank())
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
