package com.quotr.customers.config;

import com.quotr.tenant.generated.ApiClient;
import com.quotr.tenant.generated.api.TenantsApi;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TenantApiClientConfig {

    @Bean
    public ClientHttpRequestInterceptor jwtBearerTokenPropagationInterceptor() {
        return (request, body, execution) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
                String token = jwtAuthenticationToken.getToken().getTokenValue();
                if (StringUtils.hasText(token)) {
                    request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                }
            }
            return execution.execute(request, body);
        };
    }

    @Bean
    public TenantsApi tenantsApi(@Value("${quotr.tenant.base-url:http://localhost:5004/api/v1}") String baseUrl,
                                 ClientHttpRequestInterceptor jwtBearerTokenPropagationInterceptor) {
        // ADR-0001 requires a hanging quotr-tenant-service to surface as 503, not hang the
        // caller indefinitely, which RestTemplate's own unbounded default would otherwise do.
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofSeconds(3))
                        .withReadTimeout(Duration.ofSeconds(5)));
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.getInterceptors().add(jwtBearerTokenPropagationInterceptor);

        ApiClient apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath(baseUrl);
        return new TenantsApi(apiClient);
    }
}
