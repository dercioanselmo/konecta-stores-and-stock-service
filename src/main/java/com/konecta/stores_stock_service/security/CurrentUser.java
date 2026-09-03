package com.konecta.stores_stock_service.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static String userId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        throw new IllegalStateException("Authentication is not a JWT token");
    }

    public static boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN");
    }

    public static boolean isMerchantStaff(Authentication authentication) {
        return hasRole(authentication, "ROLE_MERCHANT_STAFF");
    }

    /**
     * Returns the {@code shopId} claim from the JWT, or {@code null} if absent.
     * MERCHANT_STAFF tokens carry this claim to scope the staff member to one shop.
     */
    public static UUID shopId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String raw = jwt.getClaimAsString("shopId");
            return raw != null ? UUID.fromString(raw) : null;
        }
        return null;
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
