package com.cloud.accountsystem.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequestDTO {

    // 兩個欄位皆為選填，null 表示不更新該欄位
    @Size(max = 50)
    private String displayName;

    @Email
    @Size(max = 100)
    private String email;
}
