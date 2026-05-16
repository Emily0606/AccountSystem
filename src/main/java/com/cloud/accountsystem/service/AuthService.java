package com.cloud.accountsystem.service;

import com.cloud.accountsystem.dto.request.LoginRequestDTO;
import com.cloud.accountsystem.dto.request.RegisterRequestDTO;
import com.cloud.accountsystem.dto.response.AuthResponseDTO;
import com.cloud.accountsystem.entity.User;
import com.cloud.accountsystem.entity.UserStatus;
import com.cloud.accountsystem.exception.AccountLockedException;
import com.cloud.accountsystem.exception.ConflictException;
import com.cloud.accountsystem.exception.UnauthorizedException;
import com.cloud.accountsystem.repository.UserRepository;
import com.cloud.accountsystem.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    public void register(RegisterRequestDTO request) {
        if (userRepository.existsByAccount(request.getAccount())) {
            throw new ConflictException("帳號已存在");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email 已被使用");
        }

        User user = User.builder()
                .account(request.getAccount())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByAccount(request.getAccount())
                // 帳號不存在時回傳與密碼錯誤相同的訊息，避免讓外部探知帳號是否存在
                .orElseThrow(() -> new UnauthorizedException("帳號或密碼錯誤"));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AccountLockedException("帳號已被鎖定，請聯繫管理員");
        }

        // DELETED 帳號視同帳號不存在，同樣回傳模糊錯誤
        if (user.getStatus() == UserStatus.DELETED) {
            throw new UnauthorizedException("帳號或密碼錯誤");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("帳號或密碼錯誤");
        }

        return AuthResponseDTO.builder()
                .accessToken(jwtUtil.generateToken(user))
                .tokenType("Bearer")
                .expiresIn(expirationMs / 1000)
                .build();
    }
}
