package com.cloud.accountsystem.controller.v1;

import com.cloud.accountsystem.dto.request.LoginRequestDTO;
import com.cloud.accountsystem.dto.response.LoginResponsetDTO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/v1/auth")
public class UsersController {

    @GetMapping("/test")
    public String test(){
        System.out.println("Hi!");
        return "Hello World";
    }

    @PostMapping("/login")
    public LoginResponsetDTO login(@RequestBody LoginRequestDTO request) {
        System.out.println(request.getAccount());
        System.out.println(request.getPwdHash());
        return new LoginResponsetDTO(1, "eyJhbGciOiJIUzI1NiIsInR5cCI6Ikp", 3600);
    }

}