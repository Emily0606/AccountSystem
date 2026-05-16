package com.cloud.accountsystem.repository;

import com.cloud.accountsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 登入時以帳號查詢使用者
    Optional<User> findByAccount(String account);

    // existsBy 轉譯為 SELECT COUNT(*) > 0，比 findBy 少載入整個 Entity，僅用於重複性檢查
    boolean existsByAccount(String account);
    boolean existsByEmail(String email);
}
