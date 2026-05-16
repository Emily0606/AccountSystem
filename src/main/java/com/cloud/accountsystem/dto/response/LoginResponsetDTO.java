package com.cloud.accountsystem.dto.response;

public class LoginResponsetDTO {

    private int id;
    private String token;
    private int expiresIn;

    public LoginResponsetDTO() {
    }

    public LoginResponsetDTO(int id, String token, int expiresIn) {
        this.id = id;
        this.token = token;
        this.expiresIn = expiresIn;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }

}
