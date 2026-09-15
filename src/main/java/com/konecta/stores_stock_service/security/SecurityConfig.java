package com.konecta.stores_stock_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${konecta.cors.allowed-origins}")
    private List<String> corsAllowedOrigins;

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtRolesConverter jwtRolesConverter,
            ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
                // Only /media/** actually needs this — every other endpoint here is
                // called server-to-server from the Next.js BFF, never directly by a
                // browser. /media/** exists because there's no S3 to upload straight
                // to right now (suspended AWS account, see
                // LocalFilesystemObjectStorageService) — the browser PUTs/GETs this
                // service directly instead, which means it's now subject to the
                // browser's own CORS rules the way S3's bucket CORS used to be.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // More specific than the /api/v1/shops/** permitAll below, and must
                        // come first -- authorizeHttpRequests matches in declaration order.
                        // Stock commit runs with the customer's own JWT (any role), it is
                        // deliberately not part of the public browsing surface.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/shops/*/stock/commit")
                        .authenticated()
                        .requestMatchers("/actuator/health/**", "/api/v1/meta/**", "/api/v1/shops", "/api/v1/shops/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/media/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(RestAuthEntryPoints.unauthenticated(objectMapper))
                        .accessDeniedHandler(RestAuthEntryPoints.accessDenied(objectMapper)))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtRolesConverter)));
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "PUT", "HEAD"));
        configuration.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/media/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(
            org.springframework.context.ApplicationEventPublisher publisher) {
        return new DefaultAuthenticationEventPublisher(publisher);
    }
}
