# quotr-customers-service

Spring Boot backend service for the Quotr `customers` bounded context. The service implements the published `quotr-customers-schema` Customer API contract and provides authenticated create, read, update, list/search, and soft-delete operations for user-owned customer records. Customer records remain independent from quotes in this increment.

## Runtime configuration

| Setting | Required | Default | Sensitive | Description / example |
| --- | --- | --- | --- | --- |
| `SERVER_PORT` / `server.port` | No | `8080` | No | HTTP port for the service. Example: `8080`. |
| `SPRING_DATASOURCE_URL` / `spring.datasource.url` | Yes | _none_ | No | PostgreSQL JDBC URL. Example: `jdbc:postgresql://postgres:5432/quotr_customers`. |
| `SPRING_DATASOURCE_USERNAME` / `spring.datasource.username` | Yes | _none_ | Usually | PostgreSQL username. Example: `quotr_customers`. |
| `SPRING_DATASOURCE_PASSWORD` / `spring.datasource.password` | Yes | _none_ | Yes | PostgreSQL password. Supply externally; do not bake into images. |
| `SPRING_LIQUIBASE_CHANGE_LOG` / `spring.liquibase.change-log` | No | `classpath:db/changelog/db.changelog-master.yaml` | No | Liquibase changelog location. |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` / `spring.security.oauth2.resourceserver.jwt.issuer-uri` | One JWT validator is required for protected operations | _empty_ | No | OIDC issuer URI used to validate JWTs, for example a Firebase/OIDC issuer. |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` / `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | One JWT validator is required for protected operations | _empty_ | No | JWK set URI used to validate JWTs when issuer discovery is not used. |
| `openapi.quotrCustomersSchema.base-path` | No | `/api/v1` | No | Generated Customer API base path. Keep aligned with the published contract unless an approved deployment route overrides it. |

Customer operations derive the owner id from the authenticated JWT (`user_id`, then `uid`, then `sub`) and enforce that owner id in all customer queries and mutations. If no JWT validation endpoint is configured, protected customer operations reject bearer tokens rather than accepting unvalidated tokens.

## HTTP API

The service exposes only the generated Customer API surface from the published contract, under `/api/v1` by default:

- `POST /api/v1/customers`
- `GET /api/v1/customers/{customerId}`
- `GET /api/v1/customers` with optional `q`, `page`, and `size` query parameters for list/search
- `PUT /api/v1/customers/{customerId}`
- `DELETE /api/v1/customers/{customerId}` for soft-delete

Search is partial and case-insensitive over active owned customer name, email, and address fields only. Address matching includes the stored street/address line exposed as `address.streetAddress`, `address.postalCode`, `address.city`, and `address.country` in the existing Customer API response. Phone-number search, CRM fields, quote linking, sharing, and hard-delete are intentionally not implemented.

The existing authenticated `GET /api/v1/customers?q=...&page=...&size=...` contract is the Customer-side support point for quote worksite identity. A caller such as `quotr-quotes-service` must call this API with the end user's propagated JWT; the customers service then applies the same owner and soft-delete filtering as direct end-user calls. Results include each stable Customer `id` plus structured address fields, which is sufficient for quotes to match associated Customer identifiers and derive customer-backed quote address information without a customer-contract change or shared database access.

## Service-only resources

Version `1.1.0` of the contract adds one operation tagged `service-only`, which generates into `ServiceOnlyApi`:

- `GET /api/v1/tenants/{tenantId}/customers/{customerId}` (`getCustomerByTenant`) returns the customer with the existing `Customer` schema. One query by tenant id and customer id excludes soft-deleted customers, so another tenant's customer, an unknown customer and a deleted customer all answer 404.

The operation is reachable only by a backend service identity: the JWT must carry the claim `role` with the value `service` (see the quotr product ADR-0002). `SecurityConfig` maps that claim to the authority `ROLE_SERVICE` next to the existing `scope` mapping, and the operation is guarded with `@PreAuthorize("hasRole('SERVICE')")` on its controller method in `ServiceOnlyApiController`. A caller without the role, including a tenant user asking for their own tenant, receives 403; no token or an invalid token receives 401.

A service caller has no tenant, so `TenantContextFilter` skips tenant resolution for a principal holding `ROLE_SERVICE` (decided from the authenticated principal, not from the request path). Ordinary end-user requests still resolve the tenant as before, and a service principal calling an ordinary operation fails because no tenant context exists.

The role claim is only trusted because JWT signatures are verified. Never add an unverified or QA-only JWT decoder to this service. A new operation with the `service-only` tag must carry the same annotation: `ServiceOnlyApiSecurityIntegrationTest` fails when an implementing method does not.

## Readiness and health

Actuator health endpoints are intentionally unauthenticated so platform probes can call them:

- `GET /actuator/health`
- `GET /actuator/health/readiness`
- `GET /actuator/health/liveness`

Management web exposure is limited to health. Customer API operations remain protected by OAuth2 resource-server authentication.

## Persistence

The service uses Spring Data JPA with PostgreSQL. Schema changes are managed by Liquibase under `src/main/resources/db/changelog`. Customer rows use UUID resource identifiers, an authenticated owner id, contact fields, complete optional address fields (`streetAddress`, `postalCode`, `city`, and `country` at the API boundary), `created_at`/`updated_at` timestamps, and a `deleted_at` soft-delete marker. Normal detail, list, and search behavior excludes rows with `deleted_at` set.

## Contract generation

Server API interfaces and transport models are generated during the Maven `generate-sources` phase from the published ApiCurio/OpenAPI contract. Generated output is not committed.

Maven properties are split so registry location and contract identity can be overridden independently:

```xml
<apicurio.registry.url>http://api-registry.apprigger.com:18080</apicurio.registry.url>
<apicurio.registry.api.path>/apis/registry/v2</apicurio.registry.api.path>
<apicurio.groupId>com.apprigger.quotr</apicurio.groupId>
<contract.artifactId>quotr-customers-schema</contract.artifactId>
<contract.version>1.1.0</contract.version>
<contract.url>${apicurio.registry.url}${apicurio.registry.api.path}/groups/${apicurio.groupId}/artifacts/${contract.artifactId}/versions/${contract.version}</contract.url>
```

Example override:

```bash
./mvnw verify \
  -Dapicurio.registry.url=http://api-registry.apprigger.com:18080 \
  -Dapicurio.registry.api.path=/apis/registry/v2 \
  -Dapicurio.groupId=com.apprigger.quotr \
  -Dcontract.artifactId=quotr-customers-schema \
  -Dcontract.version=1.0.0
```

## Container image

The repository includes a Dockerfile for the orchestrator/runtime packaging flow. Build tooling supplies the image name and tag; the Dockerfile is independent of artifact version and does not embed environment-specific configuration.

When running the container, provide database and JWT settings through environment variables. The container exposes port `8080` by default and uses `GET /actuator/health/readiness` for its internal health check.

## Tenant client

The tenant of the caller is resolved with `GET /tenant` through a client generated from the published `quotr-tenant-schema` (`tenant.contract.version`, now `1.0.0`). Version `1.0.0` of the contract lists `NL` next to `DK` as a country of a tenant, so a company in the Netherlands is read like one in Denmark; the client of version `0.2.0` knew only `DK`, and every request of a Dutch company was answered `503 TENANT_SERVICE_UNAVAILABLE`. `TenantClientCountryTest` decodes the responses of the tenant service for a Dutch company, with an address that has only a country and with a complete one, and for a Danish company. Only the tenant and member identifiers are used; the country plays no part in this service.

