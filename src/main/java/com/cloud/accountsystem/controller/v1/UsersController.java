package com.cloud.accountsystem.controller.v1;

import com.cloud.accountsystem.dto.request.ChangePasswordRequestDTO;
import com.cloud.accountsystem.dto.request.UpdateProfileRequestDTO;
import com.cloud.accountsystem.dto.response.UserProfileDTO;
import com.cloud.accountsystem.entity.User;
import com.cloud.accountsystem.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UsersController {

    private final UserService userService;

    @GetMapping("/me")
    public UserProfileDTO getMe(@AuthenticationPrincipal User user) {
        return userService.getMe(user);
    }

    @PatchMapping("/me")
    public UserProfileDTO updateMe(@AuthenticationPrincipal User user,
                                   @Valid @RequestBody UpdateProfileRequestDTO request) {
        return userService.updateMe(user, request);
    }

    // 修改密碼需驗證舊密碼，成功後回傳 204，不重新發 Token（由 Client 自行決定是否重新登入）
    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal User user,
                               @Valid @RequestBody ChangePasswordRequestDTO request) {
        userService.changePassword(user, request);
    }

    // 軟刪除：status 設為 DELETED，並撤銷所有 Refresh Token 使現有 Session 立即失效
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal User user) {
        userService.deleteMe(user);
    }
}
