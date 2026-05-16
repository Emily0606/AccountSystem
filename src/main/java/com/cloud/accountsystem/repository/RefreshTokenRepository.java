package com.cloud.accountsystem.repository;

import com.cloud.accountsystem.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // @Modifying：告知 Spring Data 此為寫入操作（DELETE/UPDATE），否則會拋出例外
    // 使用自訂 @Query 而非衍生方法名稱：OR 跨欄位條件無法用方法名稱乾淨表達
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now OR r.revoked = true")
    void deleteExpiredAndRevoked(LocalDateTime now);

    // 已被撤銷的 Token 再次送來時（代表 Token 可能被竊取後重複使用）
    // 無法確認哪個 Session 安全，直接把該使用者所有有效 Token 全部撤銷，強制重新登入
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId AND r.revoked = false")
    void revokeAllActiveByUserId(Long userId);
}
