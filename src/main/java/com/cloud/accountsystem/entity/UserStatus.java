package com.cloud.accountsystem.entity;

public enum UserStatus {
    ACTIVE,
    // 連續登入失敗達上限後由系統鎖定，防止暴力破解
    LOCKED,
    // 軟刪除：保留資料庫紀錄，不實際 DELETE
    DELETED
}
