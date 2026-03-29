package com.gateway.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Integration Test for Phase 1 of the API Gateway Platform.
 *
 * <p>This test validates the complete flow from user registration through API
 * invocation via the gateway. It requires all services to be running:</p>
 * <ul>
 *     <li>identity-service on port 8081</li>
 *     <li>management-api on port 8082</li>
 *     <li>gateway-runtime on port 8080</li>
 * </ul>
 *
 * <p>Prerequisites:</p>
 * <ul>
 *     <li>PostgreSQL running on localhost:5432 with the gateway database</li>
 *     <li>RabbitMQ running on localhost:5672</li>
 *     <li>Seed admin account: admin@gateway.local / changeme</li>
 *     <li>A backend upstream service to proxy to (e.g., httpbin on port 9999)</li>
 * </ul>
 *
 * <p>Run with: {@code mvn test -pl gateway-runtime -Dtest=E2EIntegrationTest -Dspring.profiles.active=e2e}</p>
 *
 * @see <a href="PHASE_BUILD_PLAN.txt">TASK 1.12: End-to-End Integration Test</a>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("e2e")
class E2EIntegrationTest {

    // ---------------------------------------------------------------------------
    // Service URLs (configurable via system properties)
    // ---------------------------------------------------------------------------
    static final String IDENTITY_URL = System.getProperty("e2e.identity.url", "http://localhost:8081");
    static final String MANAGEMENT_URL = System.getProperty("e2e.management.url", "http://localhost:8082");
    static final String GATEWAY_URL = System.getProperty("e2e.gateway.url", "http://localhost:8080");

    // ---------------------------------------------------------------------------
    // Test user credentials
    // ---------------------------------------------------------------------------
    static final String ADMIN_EMAIL = "admin@gateway.local";
    static final String ADMIN_PASSWORD = "changeme";
    static final String DEVELOPER_EMAIL = "dev-e2e-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    static final String DEVELOPER_PASSWORD = "Dev!Secure123";
    static final String DEVELOPER_NAME = "E2E Test Developer";

    // ---------------------------------------------------------------------------
    // Shared state across ordered tests
    // ---------------------------------------------------------------------------
    static String adminJwt;
    static String developerJwt;
    static String developerId;
    static String applicationId;
    static String apiKeyFull;
    static String apiId;
    static String routeId;
    static String planId;
    static String subscriptionId;

    // ---------------------------------------------------------------------------
    // HTTP client & JSON mapper
    // ---------------------------------------------------------------------------
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // =========================================================================
    // STEP 1: Register a developer user
    // =========================================================================
    @Test
    @Order(1)
    void registerDeveloper() throws Exception {
        System.out.println("[E2E] Step 1 - Registering developer: " + DEVELOPER_EMAIL);

        // TODO: Adjust the endpoint path and request body to match the actual
        //       identity-service registration API once implemented.
        var body = Map.of(
                "email", DEVELOPER_EMAIL,
                "password", DEVELOPER_PASSWORD,
                "firstName", "E2E",
                "lastName", "Developer",
                "displayName", DEVELOPER_NAME
        );

        var response = restClient.post()
                .uri(IDENTITY_URL + "/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Registration should return 201 CREATED");

        JsonNode json = objectMapper.readTree(response.getBody());
        developerId = json.path("id").asText();
        assertFalse(developerId.isBlank(), "Response must contain a user ID");

        System.out.println("[E2E] Step 1 - Developer registered: id=" + developerId);
    }

    // =========================================================================
    // STEP 2: Login as seed admin
    // =========================================================================
    @Test
    @Order(2)
    void loginAsAdmin() throws Exception {
        System.out.println("[E2E] Step 2 - Logging in as admin: " + ADMIN_EMAIL);

        // TODO: Adjust endpoint and body to match actual identity-service login API.
        //       This may be an OAuth2 token endpoint or a custom login endpoint.
        var body = Map.of(
                "email", ADMIN_EMAIL,
                "password", ADMIN_PASSWORD
        );

        var response = restClient.post()
                .uri(IDENTITY_URL + "/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin login should return 200 OK");

        JsonNode json = objectMapper.readTree(response.getBody());
        adminJwt = json.path("accessToken").asText();
        assertFalse(adminJwt.isBlank(), "Response must contain an access token");

        System.out.println("[E2E] Step 2 - Admin JWT obtained (length=" + adminJwt.length() + ")");
    }

    // =========================================================================
    // STEP 3: Admin creates an API definition
    // =========================================================================
    @Test
    @Order(3)
    void createApiAsAdmin() throws Exception {
        System.out.println("[E2E] Step 3 - Creating API as admin");
        assertNotNull(adminJwt, "Admin JWT is required (step 2 must pass first)");

        // TODO: Adjust endpoint path, request body, and response fields to
        //       match the actual management-api once implemented.
        var body = Map.of(
                "name", "E2E Test API",
                "description", "API created by end-to-end integration test",
                "version", "1.0.0",
                "basePath", "/e2e-test",
                "protocol", "REST"
        );

        var response = restClient.post()
                .uri(MANAGEMENT_URL + "/api/v1/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "API creation should return 201 CREATED");

        JsonNode json = objectMapper.readTree(response.getBody());
        apiId = json.path("id").asText();
        assertFalse(apiId.isBlank(), "Response must contain an API ID");

        System.out.println("[E2E] Step 3 - API created: id=" + apiId);
    }

    // =========================================================================
    // STEP 4: Admin adds a route to the API
    // =========================================================================
    @Test
    @Order(4)
    void addRouteToApi() throws Exception {
        System.out.println("[E2E] Step 4 - Adding route to API: " + apiId);
        assertNotNull(apiId, "API ID is required (step 3 must pass first)");

        // TODO: Adjust endpoint, body, and upstream URL to match actual
        //       management-api route configuration.
        //       The upstream URL should point to a real backend for full E2E,
        //       or a stub like httpbin running locally.
        var body = Map.of(
                "path", "/hello",
                "method", "GET",
                "upstreamUrl", "http://localhost:9999/get",
                "stripPrefix", true
        );

        var response = restClient.post()
                .uri(MANAGEMENT_URL + "/api/v1/apis/" + apiId + "/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Route creation should return 201 CREATED");

        JsonNode json = objectMapper.readTree(response.getBody());
        routeId = json.path("id").asText();
        assertFalse(routeId.isBlank(), "Response must contain a route ID");

        System.out.println("[E2E] Step 4 - Route added: id=" + routeId);
    }

    // =========================================================================
    // STEP 5: Admin creates a subscription plan
    // =========================================================================
    @Test
    @Order(5)
    void createPlan() throws Exception {
        System.out.println("[E2E] Step 5 - Creating subscription plan for API: " + apiId);
        assertNotNull(apiId, "API ID is required (step 3 must pass first)");

        // TODO: Adjust to match actual plan creation API.
        //       Rate limit is intentionally low (5 req/min) so we can test 429.
        var body = Map.of(
                "name", "E2E Test Plan",
                "description", "Low-limit plan for E2E testing",
                "apiId", apiId,
                "rateLimitRequests", 5,
                "rateLimitWindowSeconds", 60,
                "requiresApproval", false
        );

        var response = restClient.post()
                .uri(MANAGEMENT_URL + "/api/v1/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Plan creation should return 201 CREATED");

        JsonNode json = objectMapper.readTree(response.getBody());
        planId = json.path("id").asText();
        assertFalse(planId.isBlank(), "Response must contain a plan ID");

        System.out.println("[E2E] Step 5 - Plan created: id=" + planId);
    }

    // =========================================================================
    // STEP 6: Admin publishes the API
    // =========================================================================
    @Test
    @Order(6)
    void publishApi() throws Exception {
        System.out.println("[E2E] Step 6 - Publishing API: " + apiId);
        assertNotNull(apiId, "API ID is required (step 3 must pass first)");

        // TODO: Adjust to match actual publish endpoint.
        var response = restClient.post()
                .uri(MANAGEMENT_URL + "/api/v1/apis/" + apiId + "/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt)
                .retrieve()
                .toEntity(String.class);

        // Accept both 200 OK and 204 No Content
        int statusCode = response.getStatusCode().value();
        assertTrue(statusCode == 200 || statusCode == 204,
                "Publish should return 200 or 204, got: " + statusCode);

        System.out.println("[E2E] Step 6 - API published successfully");
    }

    // =========================================================================
    // STEP 7: Login as the developer registered in step 1
    // =========================================================================
    @Test
    @Order(7)
    void loginAsDeveloper() throws Exception {
        System.out.println("[E2E] Step 7 - Logging in as developer: " + DEVELOPER_EMAIL);

        // TODO: If email verification is required before login, this test
        //       will need a prior step to verify the email (e.g., calling an
        //       internal verification endpoint or reading the token from DB).
        var body = Map.of(
                "email", DEVELOPER_EMAIL,
                "password", DEVELOPER_PASSWORD
        );

        var response = restClient.post()
                .uri(IDENTITY_URL + "/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Developer login should return 200 OK");

        JsonNode json = objectMapper.readTree(response.getBody());
        developerJwt = json.path("accessToken").asText();
        assertFalse(developerJwt.isBlank(), "Response must contain an access token");

        System.out.println("[E2E] Step 7 - Developer JWT obtained (length=" + developerJwt.length() + ")");
    }

    // =========================================================================
    // STEP 8: Developer creates an application
    // =========================================================================
    @Test
    @Order(8)
    void createApplication() throws Exception {
        System.out.println("[E2E] Step 8 - Developer creating application");
        assertNotNull(developerJwt, "Developer JWT is required (step 7 must pass first)");

        // TODO: Adjust to match actual application creation API.
        var body = Map.of(
                "name", "E2E Test App",
                "description", "Application created by E2E test"
        );

        var response = restClient.post()
                .uri(MANAGEMENT_URL + "/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + developerJwt)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Application creation should return 201 CREATED");

        JsonNode json = objectMapper.readTree(response.getBody());
        applicationId = json.path("id").asText();
        assertFalse(applicationId.isBlank(), "Response must contain an application ID");

        System.out.println("[E2E] Step 8 - Application created: id=" + applicationId);
    }

    // =========================================================================
    // STEP 9: Developer generates an API key for the application
    // =========================================================================
    @Test
    @Order(9)
    void generateApiKey() throws Exception {
        System.out.println("[E2E] Step 9 - Generating API key for application: " + applicationId);
        assertNotNull(applicationId, "Application ID is required (step 8 must pass first)");

        // TODO: Adjust to match actual API key generation endpoint.
        //       The full key is only returned once at creation time.
        var body = Map.of(
                "name", "E2E Test Key",
                "applicationId", applicationId
        );

        var response = restClient.post()
                .uri(IDENTITY_URL + "/api/v1/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + developerJwt)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "API key generation should return 201 CREATED");

        JsonNode json = objectMapper.readTree(response.getBody());
        apiKeyFull = json.path("key").asText();
        assertFalse(apiKeyFull.isBlank(), "Response must contain the full API key");

        System.out.println("[E2E] Step 9 - API key generated (prefix=" + apiKeyFull.substring(0, Math.min(8, apiKeyFull.length())) + "...)");
    }

    // =========================================================================
    // STEP 10: Developer subscribes the application to the plan
    // =========================================================================
    @Test
    @Order(10)
    void subscribeToPlan() throws Exception {
        System.out.println("[E2E] Step 10 - Subscribing to plan: " + planId);
        assertNotNull(applicationId, "Application ID is required (step 8 must pass first)");
        assertNotNull(planId, "Plan ID is required (step 5 must pass first)");

        // TODO: Adjust to match actual subscription creation API.
        var body = Map.of(
                "applicationId", applicationId,
                "planId", planId
        );

        var response = restClient.post()
                .uri(MANAGEMENT_URL + "/api/v1/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + developerJwt)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Subscription should return 201 CREATED");

        JsonNode json = objectMapper.readTree(response.getBody());
        subscriptionId = json.path("id").asText();
        assertFalse(subscriptionId.isBlank(), "Response must contain a subscription ID");

        System.out.println("[E2E] Step 10 - Subscribed: id=" + subscriptionId);
    }

    // =========================================================================
    // STEP 11: Call the API through the gateway with a valid API key
    // =========================================================================
    @Test
    @Order(11)
    void callApiWithApiKey_expect200() throws Exception {
        System.out.println("[E2E] Step 11 - Calling API via gateway with valid API key");
        assertNotNull(apiKeyFull, "API key is required (step 9 must pass first)");

        // TODO: The gateway URL path must match the basePath + route path
        //       configured in steps 3 and 4. Adjust header name if different.
        var response = restClient.get()
                .uri(GATEWAY_URL + "/e2e-test/hello")
                .header("X-API-Key", apiKeyFull)
                .retrieve()
                .toEntity(String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Gateway should proxy the request and return 200 OK");

        System.out.println("[E2E] Step 11 - API call succeeded with API key: " + response.getStatusCode());
    }

    // =========================================================================
    // STEP 12: Call the API with an invalid API key
    // =========================================================================
    @Test
    @Order(12)
    void callApiWithBadKey_expect401() {
        System.out.println("[E2E] Step 12 - Calling API via gateway with invalid API key");

        HttpClientErrorException exception = assertThrows(
                HttpClientErrorException.class,
                () -> restClient.get()
                        .uri(GATEWAY_URL + "/e2e-test/hello")
                        .header("X-API-Key", "gw_invalid_key_00000000000000000000")
                        .retrieve()
                        .toEntity(String.class)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode(),
                "Gateway should return 401 for invalid API key");

        System.out.println("[E2E] Step 12 - Bad key correctly rejected: " + exception.getStatusCode());
    }

    // =========================================================================
    // STEP 13: Call the API with a JWT token (no API key)
    // =========================================================================
    @Test
    @Order(13)
    void callApiWithJwt_expect200() throws Exception {
        System.out.println("[E2E] Step 13 - Calling API via gateway with developer JWT");
        assertNotNull(developerJwt, "Developer JWT is required (step 7 must pass first)");

        // TODO: This test assumes the API is configured to accept JWT auth
        //       in addition to API key auth. If the API requires a subscription
        //       for JWT-based access, the developer must already be subscribed.
        var response = restClient.get()
                .uri(GATEWAY_URL + "/e2e-test/hello")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + developerJwt)
                .retrieve()
                .toEntity(String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Gateway should accept JWT authentication and return 200 OK");

        System.out.println("[E2E] Step 13 - API call succeeded with JWT: " + response.getStatusCode());
    }

    // =========================================================================
    // STEP 14: Developer cannot access admin-only endpoints (RBAC)
    // =========================================================================
    @Test
    @Order(14)
    void developerCannotAccessAdminEndpoint_expect403() {
        System.out.println("[E2E] Step 14 - Testing RBAC: developer accessing admin endpoint");
        assertNotNull(developerJwt, "Developer JWT is required (step 7 must pass first)");

        // TODO: Adjust to a known admin-only endpoint in the management-api.
        //       The developer JWT should not have ROLE_ADMIN, so the request
        //       should be rejected with 403 Forbidden.
        HttpClientErrorException exception = assertThrows(
                HttpClientErrorException.class,
                () -> restClient.get()
                        .uri(MANAGEMENT_URL + "/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + developerJwt)
                        .retrieve()
                        .toEntity(String.class)
        );

        int statusCode = exception.getStatusCode().value();
        assertTrue(statusCode == 403 || statusCode == 401,
                "Admin endpoint should return 403 or 401 for developer, got: " + statusCode);

        System.out.println("[E2E] Step 14 - RBAC correctly enforced: " + exception.getStatusCode());
    }

    // =========================================================================
    // STEP 15: Admin revokes API key, then gateway rejects within 5 seconds
    // =========================================================================
    @Test
    @Order(15)
    void revokeApiKey_thenCallReturns401() throws Exception {
        System.out.println("[E2E] Step 15 - Admin revoking API key, then testing gateway rejection");
        assertNotNull(adminJwt, "Admin JWT is required (step 2 must pass first)");
        assertNotNull(apiKeyFull, "API key is required (step 9 must pass first)");

        // --- Revoke the API key via identity-service ---
        // TODO: Adjust the revocation endpoint. This may require the key ID
        //       rather than the full key. Adjust accordingly.
        var revokeResponse = restClient.delete()
                .uri(IDENTITY_URL + "/api/v1/api-keys/" + apiKeyFull.substring(0, 8))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt)
                .retrieve()
                .toEntity(String.class);

        int revokeStatus = revokeResponse.getStatusCode().value();
        assertTrue(revokeStatus == 200 || revokeStatus == 204,
                "Key revocation should return 200 or 204, got: " + revokeStatus);

        System.out.println("[E2E] Step 15 - API key revoked. Waiting for cache invalidation...");

        // --- Wait for cache invalidation via RabbitMQ (up to 5 seconds) ---
        // The Caffeine cache should be invalidated via RabbitMQ fanout within
        // a few hundred milliseconds. We poll every 500ms for up to 5 seconds.
        boolean rejected = false;
        for (int attempt = 1; attempt <= 10; attempt++) {
            Thread.sleep(500);
            try {
                restClient.get()
                        .uri(GATEWAY_URL + "/e2e-test/hello")
                        .header("X-API-Key", apiKeyFull)
                        .retrieve()
                        .toEntity(String.class);
                System.out.println("[E2E] Step 15 - Attempt " + attempt + ": still accepted (cache not yet invalidated)");
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    rejected = true;
                    System.out.println("[E2E] Step 15 - Attempt " + attempt + ": correctly rejected (401) after " + (attempt * 500) + "ms");
                    break;
                }
                throw e; // unexpected error
            }
        }

        assertTrue(rejected, "Revoked API key must be rejected within 5 seconds");
        System.out.println("[E2E] Step 15 - Cache invalidation verified within SLA");
    }
}
