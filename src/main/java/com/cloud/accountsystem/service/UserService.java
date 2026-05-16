package com.cloud.accountsystem.service;

import com.cloud.accountsystem.dto.request.ChangePasswordRequestDTO;
import com.cloud.accountsystem.dto.request.UpdateProfileRequestDTO;
import com.cloud.accountsystem.dto.response.UserProfileDTO;
import com.cloud.accountsystem.entity.User;
import com.cloud.accountsystem.entity.UserStatus;
import com.cloud.accountsystem.exception.ConflictException;
import com.cloud.accountsystem.exception.UnauthorizedException;
import com.cloud.accountsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserProfileDTO getMe(User user) {
        return toProfileDTO(user);
    }

    @Transactional
    public UserProfileDTO updateMe(User user, UpdateProfileRequestDTO request) {
        // 重新從 DB 取得最新資料，避免使用 SecurityContext 中的過時狀態
        User target = findActiveUser(user.getId());

        if (request.getDisplayName() != null) {
            target.setDisplayName(request.getDisplayName());
        }

        if (request.getEmail() != null && !request.getEmail().equals(target.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("Email 已被使用");
            }
            target.setEmail(request.getEmail());
        }

        return toProfileDTO(userRepository.save(target));
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequestDTO request) {
        User target = findActiveUser(user.getId());

        if (!passwordEncoder.matches(request.getOldPassword(), target.getPasswordHash())) {
            throw new UnauthorizedException("舊密碼錯誤");
        }

        target.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(target);
    }

    @Transactional
    public void deleteMe(User user) {
        User target = findActiveUser(user.getId());

        target.setStatus(UserStatus.DELETED);
        userRepository.save(target);

        // 軟刪除後立即撤銷所有 Refresh Token，使現有 Session 即時失效
        refreshTokenService.revokeAllForUser(target.getId());
    }

    private User findActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new UnauthorizedException("使用者不存在或已停用"));
    }

    private UserProfileDTO toProfileDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .account(user.getAccount())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
