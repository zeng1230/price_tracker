package com.example.price_tracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "User login credentials payload")
@Data
public class LoginDto {

    @Schema(description = "Username of the account", example = "user1")
    @NotBlank(message = "username must not be blank")
    private String username;

    @Schema(description = "Password of the account", writeOnly = true)
    @NotBlank(message = "password must not be blank")
    private String password;
}
