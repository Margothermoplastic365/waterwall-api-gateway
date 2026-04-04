package com.gateway.runtime.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevocationCheckFilterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private FilterChain filterChain;

    private RevocationCheckFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new RevocationCheckFilter(jdbcTemplate, new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()), 60);
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        response = new MockHttpServletResponse();
    }

    @Test
    void shouldPassWhenNoCredentials() throws Exception {
        when(jdbcTemplate.queryForList(any(String.class), eq(String.class)))
                .thenReturn(List.of());

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(request.getAttribute(RevocationCheckFilter.ATTR_REVOCATION_CHECKED))
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    void shouldReturn401ForRevokedApiKey() throws Exception {
        String apiKey = "my-secret-api-key";
        String keyPrefix = sha256Prefix(apiKey);

        when(jdbcTemplate.queryForList(any(String.class), eq(String.class)))
                .thenReturn(List.of(keyPrefix));

        request.addHeader("X-API-Key", apiKey);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("CREDENTIAL_REVOKED");
    }

    @Test
    void shouldPassForNonRevokedApiKey() throws Exception {
        when(jdbcTemplate.queryForList(any(String.class), eq(String.class)))
                .thenReturn(List.of("deadbeef"));

        request.addHeader("X-API-Key", "my-secret-api-key");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturn401ForRevokedJwt() throws Exception {
        String jti = "jwt-id-12345";
        String jwtPayload = "{\"sub\":\"user1\",\"jti\":\"" + jti + "\"}";
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(jwtPayload.getBytes(StandardCharsets.UTF_8));
        String fakeJwt = "eyJhbGciOiJIUzI1NiJ9." + encodedPayload + ".signature";

        when(jdbcTemplate.queryForList(any(String.class), eq(String.class)))
                .thenReturn(List.of(jti));

        request.addHeader("Authorization", "Bearer " + fakeJwt);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("CREDENTIAL_REVOKED");
    }

    @Test
    void shouldSetRevocationCheckedAttribute() throws Exception {
        when(jdbcTemplate.queryForList(any(String.class), eq(String.class)))
                .thenReturn(List.of());

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(RevocationCheckFilter.ATTR_REVOCATION_CHECKED))
                .isEqualTo(Boolean.TRUE);
    }

    /**
     * Mirrors the filter's extractApiKeyPrefix logic: SHA-256 hash, first 8 hex chars.
     */
    private String sha256Prefix(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
