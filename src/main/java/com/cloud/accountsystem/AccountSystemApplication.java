package com.cloud.accountsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling：啟用 @Scheduled 排程，RefreshTokenService 的定期清理任務需要此設定
@SpringBootApplication
@EnableScheduling
public class AccountSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountSystemApplication.class, args);
	}
}
