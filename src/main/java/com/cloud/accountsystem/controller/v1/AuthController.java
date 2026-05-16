package com.cloud.accountsystem.controller.v1;

import com.cloud.accountsystem.dto.request.LoginRequestDTO;
import com.cloud.accountsystem.dto.request.RegisterRequestDTO;
import com.cloud.accountsystem.dto.response.AuthResponseDTO;
import com.cloud.accountsystem.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 註冊成功回傳 201 Created，不回傳 Token；需另外呼叫 /login 取得
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequestDTO request) {
        authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponseDTO login(@Valid @RequestBody LoginRequestDTO request) {
        return authService.login(request);
    }
}
