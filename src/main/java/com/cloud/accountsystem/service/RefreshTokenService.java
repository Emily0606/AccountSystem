package com.cloud.accountsystem.service;

import com.cloud.accountsystem.entity.RefreshToken;
import com.cloud.accountsystem.entity.User;
import com.cloud.accountsystem.exception.UnauthorizedException;
import com.cloud.accountsystem.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-days}")
    private long refreshExpirationDays;

    // 生成原始 Token 並將雜湊值存入 DB，回傳原始 Token 給 Client
    @Transactional
    public String create(User user) {
        String rawToken = generateRawToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(rawToken))
                .expiresAt(LocalDateTime.now().plusDays(refreshExpirationDays))
                .build();
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    // 驗證傳入的原始 Token，回傳 DB 中對應的紀錄供後續操作使用
    @Transactional
    public RefreshToken validate(String rawToken) {
        String tokenHash = hashToken(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("無效的 Refresh Token"));

        if (token.isRevoked()) {
            // Token 已被撤銷卻再度送來：正常流程不會發生這種情況
            // 代表舊 Token 可能被竊取並重複使用，為安全起見撤銷該使用者所有有效 Token
            refreshTokenRepository.revokeAllActiveByUserId(token.getUser().getId());
            throw new UnauthorizedException("Refresh Token 已失效，請重新登入");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh Token 已過期，請重新登入");
        }

        return token;
    }

    @Transactional
    public void revoke(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    // 刪除帳號時使用：立即撤銷所有裝置的有效 Session
    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllActiveByUserId(userId);
    }

    // 登出使用：Token 不存在或已撤銷時靜默成功，保持冪等
    @Transactional
    public void revokeIfExists(String rawToken) {
        refreshTokenRepository.findByTokenHash(hashToken(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(this::revoke);
    }

    // 每天凌晨 3 點清除已過期或已撤銷的 Token，防止資料表無限膨脹
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredAndRevoked() {
        refreshTokenRepository.deleteExpiredAndRevoked(LocalDateTime.now());
    }

    // SecureRandom 產生 32 bytes，Base64 URL-safe 編碼後約 43 字元
    private String generateRawToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 規範強制支援的演算法，不會發生
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
