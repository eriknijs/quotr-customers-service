package com.quotr.customers.config;

import com.quotr.customers.tenant.TenantContextFilter;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    public static final String SERVICE_ROLE_CLAIM = "role";
    public static final String SERVICE_ROLE_VALUE = "service";
    public static final String SERVICE_AUTHORITY = "ROLE_SERVICE";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TenantContextFilter tenantContextFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                // ADR-0001: tenant resolution runs after JWT authentication has populated the
                // SecurityContext (so the caller's token is available to forward) and before any
                // controller (so an unresolved tenant never reaches application logic).
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            if (SERVICE_ROLE_VALUE.equals(jwt.getClaim(SERVICE_ROLE_CLAIM))) {
                authorities.add(new SimpleGrantedAuthority(SERVICE_AUTHORITY));
            }
            return authorities;
        });
        return converter;
    }
}
