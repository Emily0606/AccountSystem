-- =============================================
-- V1：初始 Schema 建立
-- 包含 users（帳號主表）與 refresh_tokens（Token 管理）
-- =============================================

-- 帳號主表
CREATE TABLE users
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    account          VARCHAR(50)                          NOT NULL UNIQUE, -- 登入帳號，全系統唯一
    email            VARCHAR(100)                         NOT NULL UNIQUE,
    password_hash    VARCHAR(255)                         NOT NULL,        -- BCrypt 雜湊，不儲存明文
    display_name     VARCHAR(50),
    status           ENUM ('ACTIVE', 'LOCKED', 'DELETED') NOT NULL,        -- LOCKED：登入失敗鎖定；DELETED：軟刪除
    login_fail_count INT                                  NOT NULL DEFAULT 0,
    created_at       TIMESTAMP                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP                            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Refresh Token 管理表
-- 儲存 SHA-256 雜湊值，原始 Token 僅回傳給 Client，不落地
CREATE TABLE refresh_tokens
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,  -- SHA-256 hex，固定 64 字元
    expires_at TIMESTAMP   NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);
