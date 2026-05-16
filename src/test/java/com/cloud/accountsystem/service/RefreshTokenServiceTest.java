package com.cloud.accountsystem.service;

import com.cloud.accountsystem.entity.RefreshToken;
import com.cloud.accountsystem.entity.User;
import com.cloud.accountsystem.entity.UserStatus;
import com.cloud.accountsystem.exception.UnauthorizedException;
import com.cloud.accountsystem.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationDays", 7L);
    }

    // ===== create =====

    @Test
    void create_success_savesHashNotRawToken() {
        User user = buildUser();
        given(refreshTokenRepository.save(any())).willAnswer(i -> i.getArgument(0));

        String rawToken = refreshTokenService.create(user);

        assertThat(rawToken).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getTokenHash()).isEqualTo(refreshTokenService.hashToken(rawToken)); // DB 只存 hash
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    // ===== validate =====

    @Test
    void validate_validToken_returnsToken() {
        String raw = "valid-raw-token";
        RefreshToken token = buildToken(false, LocalDateTime.now().plusDays(7));

        given(refreshTokenRepository.findByTokenHash(refreshTokenService.hashToken(raw))).willReturn(Optional.of(token));

        RefreshToken result = refreshTokenService.validate(raw);

        assertThat(result).isSameAs(token);
    }

    @Test
    void validate_tokenNotFound_throwsUnauthorized() {
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.validate("unknown-token"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void validate_revokedToken_revokeAllAndThrow() {
        String raw = "stolen-token";
        User user = buildUser();
        RefreshToken revokedToken = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenService.hashToken(raw))
                .revoked(true)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        given(refreshTokenRepository.findByTokenHash(refreshTokenService.hashToken(raw))).willReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> refreshTokenService.validate(raw))
                .isInstanceOf(UnauthorizedException.class);

        // 疑似 Token 重用攻擊：撤銷該使用者所有有效 Token
        verify(refreshTokenRepository).revokeAllActiveByUserId(user.getId());
    }

    @Test
    void validate_expiredToken_throwsUnauthorized() {
        String raw = "expired-token";
        RefreshToken expiredToken = buildToken(false, LocalDateTime.now().minusSeconds(1));

        given(refreshTokenRepository.findByTokenHash(refreshTokenService.hashToken(raw))).willReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> refreshTokenService.validate(raw))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ===== revoke =====

    @Test
    void revoke_setsRevokedAndSaves() {
        RefreshToken token = buildToken(false, LocalDateTime.now().plusDays(7));

        refreshTokenService.revoke(token);

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    // ===== revokeAllForUser =====

    @Test
    void revokeAllForUser_callsRepository() {
        refreshTokenService.revokeAllForUser(1L);
        verify(refreshTokenRepository).revokeAllActiveByUserId(1L);
    }

    // ===== revokeIfExists =====

    @Test
    void revokeIfExists_activeToken_revokesIt() {
        String raw = "active-token";
        RefreshToken token = buildToken(false, LocalDateTime.now().plusDays(7));

        given(refreshTokenRepository.findByTokenHash(refreshTokenService.hashToken(raw))).willReturn(Optional.of(token));

        refreshTokenService.revokeIfExists(raw);

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void revokeIfExists_alreadyRevoked_doesNothing() {
        String raw = "already-revoked-token";
        RefreshToken token = buildToken(true, LocalDateTime.now().plusDays(7));

        given(refreshTokenRepository.findByTokenHash(refreshTokenService.hashToken(raw))).willReturn(Optional.of(token));

        refreshTokenService.revokeIfExists(raw);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeIfExists_tokenNotFound_silentlySucceeds() {
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        refreshTokenService.revokeIfExists("nonexistent-token");

        verify(refreshTokenRepository, never()).save(any());
    }

    // ===== deleteExpiredAndRevoked =====

    @Test
    void deleteExpiredAndRevoked_callsRepository() {
        refreshTokenService.deleteExpiredAndRevoked();
        verify(refreshTokenRepository).deleteExpiredAndRevoked(any(LocalDateTime.class));
    }

    // ===== helpers =====

    private User buildUser() {
        return User.builder()
                .id(1L)
                .account("alice")
                .email("alice@example.com")
                .status(UserStatus.ACTIVE)
                .build();
    }

    private RefreshToken buildToken(boolean revoked, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .user(buildUser())
                .tokenHash("some-hash")
                .revoked(revoked)
                .expiresAt(expiresAt)
                .build();
    }
}
