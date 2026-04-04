package com.gateway.identity.service;

import com.gateway.common.events.EventPublisher;
import com.gateway.identity.dto.MfaSetupResponse;
import com.gateway.identity.entity.MfaSecretEntity;
import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.enums.MfaType;
import com.gateway.identity.repository.MfaSecretRepository;
import com.gateway.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

    @Mock private MfaSecretRepository mfaSecretRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private EventPublisher eventPublisher;

    private MfaService mfaService;

    private UUID userId;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        // MfaService uses constructor injection (not @RequiredArgsConstructor via Lombok),
        // so we construct it manually
        mfaService = new MfaService(mfaSecretRepository, userRepository, auditService, eventPublisher);

        userId = UUID.randomUUID();
        user = UserEntity.builder()
                .id(userId)
                .email("mfa@example.com")
                .build();
    }

    // ── setupTotp tests ─────────────────────────────────────────────────

    @Test
    void setupTotp_newUser_createsSecretAndReturnsSetupResponse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP))
                .thenReturn(Optional.empty());
        when(mfaSecretRepository.save(any(MfaSecretEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        MfaSetupResponse result = mfaService.setupTotp(userId);

        assertThat(result).isNotNull();
        assertThat(result.getSecretKey()).isNotNull().isNotEmpty();
        assertThat(result.getQrCodeDataUrl()).startsWith("data:image/png;base64,");
        assertThat(result.getRecoveryCodes()).hasSize(10);
        // All recovery codes should be unique
        assertThat(new HashSet<>(result.getRecoveryCodes())).hasSize(10);

        ArgumentCaptor<MfaSecretEntity> captor = ArgumentCaptor.forClass(MfaSecretEntity.class);
        verify(mfaSecretRepository).save(captor.capture());
        MfaSecretEntity saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(MfaType.TOTP);
        assertThat(saved.getEnabled()).isFalse();
        assertThat(saved.getSecretEncrypted()).isEqualTo(result.getSecretKey());
        assertThat(saved.getRecoveryCodes()).hasSize(10);
    }

    @Test
    void setupTotp_existingPendingSetup_replacesSecret() {
        MfaSecretEntity existing = MfaSecretEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .type(MfaType.TOTP)
                .secretEncrypted("OLD_SECRET")
                .enabled(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP))
                .thenReturn(Optional.of(existing));
        when(mfaSecretRepository.save(any(MfaSecretEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        MfaSetupResponse result = mfaService.setupTotp(userId);

        assertThat(result.getSecretKey()).isNotEqualTo("OLD_SECRET");

        ArgumentCaptor<MfaSecretEntity> captor = ArgumentCaptor.forClass(MfaSecretEntity.class);
        verify(mfaSecretRepository).save(captor.capture());
        // Should reuse the existing entity (upsert)
        assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
    }

    @Test
    void setupTotp_userNotFound_throwsIllegalArgument() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mfaService.setupTotp(unknownId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    // ── verifyAndEnableTotp tests ───────────────────────────────────────

    @Test
    void verifyAndEnableTotp_noSetup_throwsIllegalState() {
        when(mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> mfaService.verifyAndEnableTotp(userId, "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP has not been set up");
    }

    @Test
    void verifyAndEnableTotp_invalidCode_throwsIllegalArgument() {
        MfaSecretEntity entity = MfaSecretEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .type(MfaType.TOTP)
                .secretEncrypted("JBSWY3DPEHPK3PXP") // well-known test secret
                .enabled(false)
                .build();

        when(mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP))
                .thenReturn(Optional.of(entity));

        // "000000" is almost certainly wrong for any given time step
        assertThatThrownBy(() -> mfaService.verifyAndEnableTotp(userId, "999999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid TOTP code");
    }

    // ── disableTotp tests ───────────────────────────────────────────────

    @Test
    void disableTotp_notEnabled_throwsIllegalState() {
        when(mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> mfaService.disableTotp(userId, "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP is not enabled");
    }

    @Test
    void disableTotp_enabledButNotEnabled_throwsIllegalState() {
        MfaSecretEntity entity = MfaSecretEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .type(MfaType.TOTP)
                .secretEncrypted("SECRET")
                .enabled(false)
                .build();

        // findByUserIdAndType returns entity but filter(MfaSecretEntity::getEnabled)
        // will filter it out since enabled = false
        when(mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> mfaService.disableTotp(userId, "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP is not enabled");
    }

    // ── regenerateRecoveryCodes tests ───────────────────────────────────

    @Test
    void regenerateRecoveryCodes_enabled_returnsNewCodes() {
        MfaSecretEntity entity = MfaSecretEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .type(MfaType.TOTP)
                .secretEncrypted("SECRET")
                .enabled(true)
                .recoveryCodes(List.of("old1", "old2"))
                .build();

        when(mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP))
                .thenReturn(Optional.of(entity));
        when(mfaSecretRepository.save(any(MfaSecretEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        List<String> newCodes = mfaService.regenerateRecoveryCodes(userId);

        assertThat(newCodes).hasSize(10);
        assertThat(newCodes).doesNotContain("old1", "old2");

        ArgumentCaptor<MfaSecretEntity> captor = ArgumentCaptor.forClass(MfaSecretEntity.class);
        verify(mfaSecretRepository).save(captor.capture());
        assertThat(captor.getValue().getRecoveryCodes()).hasSize(10);
    }

    @Test
    void regenerateRecoveryCodes_notEnabled_throwsIllegalState() {
        when(mfaSecretRepository.findByUserIdAndType(userId, MfaType.TOTP))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> mfaService.regenerateRecoveryCodes(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP is not enabled");
    }

    // ── getEnabledFactors tests ─────────────────────────────────────────

    @Test
    void getEnabledFactors_withTotpEnabled_returnsFactorList() {
        MfaSecretEntity totpEntity = MfaSecretEntity.builder()
                .type(MfaType.TOTP)
                .enabled(true)
                .build();

        when(mfaSecretRepository.findByUserIdAndEnabledTrue(userId))
                .thenReturn(List.of(totpEntity));

        List<String> factors = mfaService.getEnabledFactors(userId);

        assertThat(factors).containsExactly("TOTP");
    }

    @Test
    void getEnabledFactors_noFactorsEnabled_returnsEmptyList() {
        when(mfaSecretRepository.findByUserIdAndEnabledTrue(userId))
                .thenReturn(List.of());

        List<String> factors = mfaService.getEnabledFactors(userId);

        assertThat(factors).isEmpty();
    }

    @Test
    void getEnabledFactors_multipleFactors_returnsAll() {
        MfaSecretEntity totpEntity = MfaSecretEntity.builder()
                .type(MfaType.TOTP)
                .enabled(true)
                .build();
        MfaSecretEntity emailEntity = MfaSecretEntity.builder()
                .type(MfaType.EMAIL)
                .enabled(true)
                .build();

        when(mfaSecretRepository.findByUserIdAndEnabledTrue(userId))
                .thenReturn(List.of(totpEntity, emailEntity));

        List<String> factors = mfaService.getEnabledFactors(userId);

        assertThat(factors).containsExactlyInAnyOrder("TOTP", "EMAIL");
    }
}
