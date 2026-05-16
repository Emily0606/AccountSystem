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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    // 連續登入失敗達此上限後鎖定帳號
    private static final int MAX_LOGIN_FAIL_COUNT = 3;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    @Transactional
    public void register(RegisterRequestDTO request) {
        if (userRepository.existsByAccount(request.getAccount())) {
            throw new ConflictException("帳號已存在");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email 已被使用");
        }

        User user = User.builder()
                .account(request.getAccount())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);
    }

    // noRollbackFor：密碼錯誤時需先 save 失敗計數再拋例外
    // 若不設定，RuntimeException 會導致 Transaction 回滾，計數永遠無法寫入 DB
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByAccount(request.getAccount())
                // 帳號不存在時回傳與密碼錯誤相同的訊息，避免讓外部探知帳號是否存在
                .orElseThrow(() -> new UnauthorizedException("帳號或密碼錯誤"));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AccountLockedException("帳號已被鎖定，請聯繫管理員");
        }

        // DELETED 帳號視同帳號不存在，同樣回傳模糊錯誤
        if (user.getStatus() == UserStatus.DELETED) {
            throw new UnauthorizedException("帳號或密碼錯誤");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int failCount = user.getLoginFailCount() + 1;
            user.setLoginFailCount(failCount);
            if (failCount >= MAX_LOGIN_FAIL_COUNT) {
                user.setStatus(UserStatus.LOCKED);
            }
            userRepository.save(user);
            throw new UnauthorizedException("帳號或密碼錯誤");
        }

        // 登入成功，重置失敗計數
        user.setLoginFailCount(0);
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    // Token Rotation：撤銷舊的 Refresh Token 並發放全新的 Access Token + Refresh Token
    @Transactional
    public AuthResponseDTO refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokenService.validate(rawRefreshToken);
        User user = token.getUser();

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("帳號狀態異常，請重新登入");
        }

        refreshTokenService.revoke(token);
        return buildAuthResponse(user);
    }

    // 冪等登出：Token 不存在或已撤銷時靜默成功
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeIfExists(rawRefreshToken);
    }

    private AuthResponseDTO buildAuthResponse(User user) {
        return AuthResponseDTO.builder()
                .accessToken(jwtUtil.generateToken(user))
                .refreshToken(refreshTokenService.create(user))
                .tokenType("Bearer")
                .expiresIn(expirationMs / 1000)
                .build();
    }
}
