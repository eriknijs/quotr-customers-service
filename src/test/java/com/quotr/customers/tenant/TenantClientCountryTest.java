package com.quotr.customers.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quotr.tenant.generated.ApiClient;
import com.quotr.tenant.generated.api.TenantsApi;
import com.quotr.tenant.generated.model.SupportedCountryCodeDTO;
import com.quotr.tenant.generated.model.TenantDTO;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * AC-F-316 server side: the tenant of the caller is read through the generated client of the published tenant schema, so a tenant
 * in the Netherlands is read as well as one in Denmark. The client of tenant schema 0.2.0 knew only DK, and the tenant filter
 * then answered 503 for every request of a Dutch company. The responses below are shaped like those of the tenant service: the
 * address is always present, and the parts of a country-only address are explicit nulls.
 */
class TenantClientCountryTest {
    private static final String BASE = "http://tenant.test/api/v1";

    private TenantDTO read(String address, String timeZone) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ApiClient apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath(BASE);
        UUID tenantId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        server.expect(requestTo(BASE + "/tenant")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"tenantId\":\"" + tenantId + "\",\"tenantMemberId\":\"" + memberId + "\",\"companyName\":\"Acme\","
                        + "\"address\":" + address + ",\"timeZone\":\"" + timeZone + "\"}", MediaType.APPLICATION_JSON));
        TenantDTO tenant = new TenantsApi(apiClient).getTenant();
        server.verify();
        assertThat(tenant.getTenantId()).isEqualTo(tenantId);
        assertThat(tenant.getTenantMemberId()).isEqualTo(memberId);
        return tenant;
    }

    @Test
    void aTenantInTheNetherlandsWhoseAddressHasOnlyACountryIsRead() {
        TenantDTO tenant = read("{\"streetAddress\":null,\"postalCode\":null,\"city\":null,\"countryCode\":\"NL\"}", "Europe/Amsterdam");

        assertThat(tenant.getAddress().getCountryCode()).isEqualTo(SupportedCountryCodeDTO.NL);
    }

    @Test
    void aTenantInTheNetherlandsWithACompleteAddressIsRead() {
        TenantDTO tenant = read("{\"streetAddress\":\"Keizersgracht 1\",\"postalCode\":\"1015 CJ\",\"city\":\"Amsterdam\",\"countryCode\":\"NL\"}", "Europe/Amsterdam");

        assertThat(tenant.getAddress().getCountryCode()).isEqualTo(SupportedCountryCodeDTO.NL);
        assertThat(tenant.getAddress().getCity()).isEqualTo("Amsterdam");
    }

    @Test
    void aTenantInDenmarkIsStillRead() {
        TenantDTO tenant = read("{\"streetAddress\":null,\"postalCode\":null,\"city\":null,\"countryCode\":\"DK\"}", "Europe/Copenhagen");

        assertThat(tenant.getAddress().getCountryCode()).isEqualTo(SupportedCountryCodeDTO.DK);
    }
}
