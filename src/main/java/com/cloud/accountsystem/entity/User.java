package com.cloud.accountsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String account;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // BCrypt 雜湊值，原始密碼不儲存
    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 50)
    private String displayName;

    // EnumType.STRING：存入欄位名稱（如 "ACTIVE"）而非數字索引，避免 Enum 順序變動造成資料錯亂
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserStatus status;

    // @Builder.Default：@Builder 預設會忽略欄位初始值，加此註解才能保留 = 0 的預設值
    @Column(nullable = false)
    @Builder.Default
    private int loginFailCount = 0;

    // @CreationTimestamp / @UpdateTimestamp：由 Hibernate 在 INSERT / UPDATE 時自動填入，不需手動設定
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
