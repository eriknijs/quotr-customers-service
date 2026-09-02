package com.quotr.customers.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuthenticatedOwnerProvider {

    public String currentOwnerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Customer operations require an authenticated caller");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String firebaseUserId = jwt.getClaimAsString("user_id");
            if (StringUtils.hasText(firebaseUserId)) {
                return firebaseUserId;
            }
            String uid = jwt.getClaimAsString("uid");
            if (StringUtils.hasText(uid)) {
                return uid;
            }
            if (StringUtils.hasText(jwt.getSubject())) {
                return jwt.getSubject();
            }
        }

        if (StringUtils.hasText(authentication.getName())) {
            return authentication.getName();
        }
        throw new IllegalStateException("Authenticated caller does not expose a stable owner identity");
    }
}
