package com.cloud.accountsystem.service;

import com.cloud.accountsystem.dto.request.LoginRequestDTO;
import com.cloud.accountsystem.dto.request.RegisterRequestDTO;
import com.cloud.accountsystem.dto.response.AuthResponseDTO;
import com.cloud.accountsystem.entity.RefreshToken;
import com.cloud.accountsystem.entity.User;
import com.cloud.accountsystem.entity.UserStatus;
import com.cloud.accountsystem.exception.AccountLockedException;
import com.cloud.accountsystem.exception.ConflictException;
import com.cloud.accountsystem.exception.UnauthorizedException;
import com.cloud.accountsystem.repository.UserRepository;
import com.cloud.accountsystem.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "expirationMs", 900000L);
    }

    // ===== register =====

    @Test
    void register_success() {
        RegisterRequestDTO req = buildRegisterRequest();

        given(userRepository.existsByAccount("alice")).willReturn(false);
        given(userRepository.existsByEmail("alice@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("$hashed");

        authService.register(req);

        verify(userRepository).save(argThat(u ->
                u.getAccount().equals("alice") &&
                u.getEmail().equals("alice@example.com") &&
                u.getStatus() == UserStatus.ACTIVE &&
                u.getLoginFailCount() == 0));
    }

    @Test
    void register_duplicateAccount_throwsConflict() {
        RegisterRequestDTO req = buildRegisterRequest();

        given(userRepository.existsByAccount("alice")).willReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("帳號已存在");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        RegisterRequestDTO req = buildRegisterRequest();

        given(userRepository.existsByAccount("alice")).willReturn(false);
        given(userRepository.existsByEmail("alice@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email 已被使用");
    }

    // ===== login =====

    @Test
    void login_success() {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setAccount("alice");
        req.setPassword("password123");

        User user = buildUser(UserStatus.ACTIVE, 0);

        given(userRepository.findByAccount("alice")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "$hashed")).willReturn(true);
        given(jwtUtil.generateToken(user)).willReturn("access-token");
        given(refreshTokenService.create(user)).willReturn("refresh-token");

        AuthResponseDTO resp = authService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("access-token");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(resp.getTokenType()).isEqualTo("Bearer");
        assertThat(resp.getExpiresIn()).isEqualTo(900L);
        verify(userRepository).save(user);
    }

    @Test
    void login_accountNotFound_throwsUnauthorized() {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setAccount("ghost");
        req.setPassword("password123");

        given(userRepository.findByAccount("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_wrongPassword_incrementsFailCountAndThrows() {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setAccount("alice");
        req.setPassword("wrong");

        User user = buildUser(UserStatus.ACTIVE, 0);

        given(userRepository.findByAccount("alice")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "$hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(user.getLoginFailCount()).isEqualTo(1);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(user);
    }

    @Test
    void login_thirdFailure_locksAccount() {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setAccount("alice");
        req.setPassword("wrong");

        User user = buildUser(UserStatus.ACTIVE, 2); // 已失敗 2 次，第 3 次觸發鎖定

        given(userRepository.findByAccount("alice")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "$hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    void login_lockedAccount_throwsAccountLocked() {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setAccount("alice");
        req.setPassword("password123");

        given(userRepository.findByAccount("alice")).willReturn(Optional.of(buildUser(UserStatus.LOCKED, 3)));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void login_deletedAccount_throwsUnauthorized() {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setAccount("alice");
        req.setPassword("password123");

        given(userRepository.findByAccount("alice")).willReturn(Optional.of(buildUser(UserStatus.DELETED, 0)));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ===== refresh =====

    @Test
    void refresh_success_rotatesToken() {
        User user = buildUser(UserStatus.ACTIVE, 0);
        RefreshToken token = RefreshToken.builder().user(user).tokenHash("hash").build();

        given(refreshTokenService.validate("raw-token")).willReturn(token);
        given(jwtUtil.generateToken(user)).willReturn("new-access-token");
        given(refreshTokenService.create(user)).willReturn("new-refresh-token");

        AuthResponseDTO resp = authService.refresh("raw-token");

        assertThat(resp.getAccessToken()).isEqualTo("new-access-token");
        assertThat(resp.getRefreshToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenService).revoke(token);
    }

    @Test
    void refresh_inactiveUser_throwsUnauthorized() {
        User user = buildUser(UserStatus.LOCKED, 3);
        RefreshToken token = RefreshToken.builder().user(user).tokenHash("hash").build();

        given(refreshTokenService.validate("raw-token")).willReturn(token);

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ===== logout =====

    @Test
    void logout_callsRevokeIfExists() {
        authService.logout("raw-token");
        verify(refreshTokenService).revokeIfExists("raw-token");
    }

    // ===== helpers =====

    private RegisterRequestDTO buildRegisterRequest() {
        RegisterRequestDTO req = new RegisterRequestDTO();
        req.setAccount("alice");
        req.setEmail("alice@example.com");
        req.setPassword("password123");
        return req;
    }

    private User buildUser(UserStatus status, int failCount) {
        return User.builder()
                .id(1L)
                .account("alice")
                .email("alice@example.com")
                .passwordHash("$hashed")
                .status(status)
                .loginFailCount(failCount)
                .build();
    }
}
