package com.gateway.identity.controller;

import com.gateway.common.auth.GatewayAuthentication;
import com.gateway.identity.dto.MfaSetupResponse;
import com.gateway.identity.dto.MfaStatusResponse;
import com.gateway.identity.dto.MfaVerifyRequest;
import com.gateway.identity.service.MfaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private MfaService mfaService;

    @InjectMocks
    private MfaController mfaController;

    @BeforeEach
    void setUpSecurityContext() {
        GatewayAuthentication auth = new GatewayAuthentication(
                USER_ID.toString(), "org-1", "user@test.com",
                List.of("USER"), List.of(), null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── setupTotp ───────────────────────────────────────────────────────

    @Test
    void setupTotp_returnsOkWithSetupResponse() {
        MfaSetupResponse expected = MfaSetupResponse.builder()
                .secretKey("BASE32SECRET")
                .qrCodeDataUrl("data:image/png;base64,abc123")
                .recoveryCodes(List.of("code1", "code2", "code3"))
                .build();

        when(mfaService.setupTotp(USER_ID)).thenReturn(expected);

        ResponseEntity<MfaSetupResponse> response = mfaController.setupTotp();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(mfaService).setupTotp(USER_ID);
    }

    // ── verifyAndEnableTotp ─────────────────────────────────────────────

    @Test
    void verifyAndEnableTotp_returnsNoContent() {
        MfaVerifyRequest request = new MfaVerifyRequest();
        request.setCode("123456");

        ResponseEntity<Void> response = mfaController.verifyAndEnableTotp(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(mfaService).verifyAndEnableTotp(USER_ID, "123456");
    }

    // ── disableTotp ─────────────────────────────────────────────────────

    @Test
    void disableTotp_returnsNoContent() {
        MfaVerifyRequest request = new MfaVerifyRequest();
        request.setCode("654321");

        ResponseEntity<Void> response = mfaController.disableTotp(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(mfaService).disableTotp(USER_ID, "654321");
    }

    // ── regenerateRecoveryCodes ─────────────────────────────────────────

    @Test
    void regenerateRecoveryCodes_returnsOkWithCodes() {
        List<String> expected = List.of("rc-1", "rc-2", "rc-3", "rc-4");
        when(mfaService.regenerateRecoveryCodes(USER_ID)).thenReturn(expected);

        ResponseEntity<List<String>> response = mfaController.regenerateRecoveryCodes();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(mfaService).regenerateRecoveryCodes(USER_ID);
    }

    // ── sendEmailOtp ────────────────────────────────────────────────────

    @Test
    void sendEmailOtp_returnsNoContent() {
        ResponseEntity<Void> response = mfaController.sendEmailOtp();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(mfaService).sendEmailOtp(USER_ID);
    }

    // ── verifyEmailOtp ──────────────────────────────────────────────────

    @Test
    void verifyEmailOtp_validCode_returnsNoContent() {
        MfaVerifyRequest request = new MfaVerifyRequest();
        request.setCode("789012");

        when(mfaService.verifyEmailOtp(USER_ID, "789012")).thenReturn(true);

        ResponseEntity<Void> response = mfaController.verifyEmailOtp(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(mfaService).verifyEmailOtp(USER_ID, "789012");
    }

    @Test
    void verifyEmailOtp_invalidCode_returnsBadRequest() {
        MfaVerifyRequest request = new MfaVerifyRequest();
        request.setCode("000000");

        when(mfaService.verifyEmailOtp(USER_ID, "000000")).thenReturn(false);

        ResponseEntity<Void> response = mfaController.verifyEmailOtp(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mfaService).verifyEmailOtp(USER_ID, "000000");
    }

    // ── getMfaStatus ────────────────────────────────────────────────────

    @Test
    void getMfaStatus_returnsOkWithStatus() {
        MfaStatusResponse expected = MfaStatusResponse.builder()
                .totpEnabled(true)
                .emailOtpEnabled(false)
                .smsOtpEnabled(false)
                .recoveryCodesRemaining(8)
                .build();

        when(mfaService.getMfaStatus(USER_ID)).thenReturn(expected);

        ResponseEntity<MfaStatusResponse> response = mfaController.getMfaStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        assertThat(response.getBody().isTotpEnabled()).isTrue();
        assertThat(response.getBody().getRecoveryCodesRemaining()).isEqualTo(8);
        verify(mfaService).getMfaStatus(USER_ID);
    }
}
