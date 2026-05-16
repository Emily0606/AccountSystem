package com.cloud.accountsystem.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponseDTO {

    private String accessToken;
    private String refreshToken;
    // 固定為 "Bearer"，告知 Client 放入 Authorization Header 的 Token 類型
    private String tokenType;
    // Access Token 有效秒數（非毫秒），供 Client 計算過期時間
    private long expiresIn;
}
