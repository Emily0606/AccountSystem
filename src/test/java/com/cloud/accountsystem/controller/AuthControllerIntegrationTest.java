package com.cloud.accountsystem.controller;

import com.cloud.accountsystem.repository.RefreshTokenRepository;
import com.cloud.accountsystem.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ===== register =====

    @Test
    void register_success_returns201() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("alice", "alice@example.com", "password123")))
                .andExpect(status().isCreated());
    }

    @Test
    void register_duplicateAccount_returns409() throws Exception {
        doRegister("alice", "alice@example.com", "password123");

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("alice", "other@example.com", "password123")))
                .andExpect(status().isConflict());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        doRegister("alice", "alice@example.com", "password123");

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("bob", "alice@example.com", "password123")))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidPayload_returns400() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("a", "not-an-email", "short")))
                .andExpect(status().isBadRequest());
    }

    // ===== login =====

    @Test
    void login_success_returnsTokens() throws Exception {
        doRegister("alice", "alice@example.com", "password123");

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alice", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        doRegister("alice", "alice@example.com", "password123");

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alice", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_threeFailures_locksAccountAndReturns403() throws Exception {
        doRegister("alice", "alice@example.com", "password123");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("alice", "wrong")))
                    .andExpect(status().isUnauthorized());
        }

        // 帳號鎖定後，密碼驗證結果無效
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alice", "password123")))
                .andExpect(status().isForbidden());
    }

    // ===== refresh =====

    @Test
    void refresh_success_returnsNewTokens() throws Exception {
        doRegister("alice", "alice@example.com", "password123");
        String refreshToken = loginAndGetRefreshToken("alice", "password123");

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("invalid-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_usedToken_returns401() throws Exception {
        doRegister("alice", "alice@example.com", "password123");
        String refreshToken = loginAndGetRefreshToken("alice", "password123");

        // 第一次 refresh 後舊 Token 被撤銷，再次使用應回傳 401
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(refreshToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    // ===== logout =====

    @Test
    void logout_success_returns204() throws Exception {
        doRegister("alice", "alice@example.com", "password123");
        String refreshToken = loginAndGetRefreshToken("alice", "password123");

        mockMvc.perform(post("/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(refreshToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_nonexistentToken_returns204() throws Exception {
        // 冪等：Token 不存在時仍回傳 204
        mockMvc.perform(post("/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("nonexistent-token")))
                .andExpect(status().isNoContent());
    }

    // ===== protected endpoints =====

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ===== helpers =====

    private String registerBody(String account, String email, String password) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "account", account,
                "email", email,
                "password", password));
    }

    private String loginBody(String account, String password) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "account", account,
                "password", password));
    }

    private String tokenBody(String refreshToken) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken));
    }

    private void doRegister(String account, String email, String password) throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(account, email, password)))
                .andExpect(status().isCreated());
    }

    private String loginAndGetRefreshToken(String account, String password) throws Exception {
        String body = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(account, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("refreshToken").asText();
    }
}
