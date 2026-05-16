package com.cloud.accountsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequestDTO {

    @NotBlank
    private String oldPassword;

    @NotBlank
    @Size(min = 8, max = 100)
    private String newPassword;
}
