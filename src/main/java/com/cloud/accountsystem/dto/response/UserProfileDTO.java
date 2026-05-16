package com.cloud.accountsystem.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserProfileDTO {

    private Long id;
    private String account;
    private String email;
    private String displayName;
    private LocalDateTime createdAt;
}
