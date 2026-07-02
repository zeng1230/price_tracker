package com.example.price_tracker.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "User login response data containing token info")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVo {

    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummy_token...")
    private String token;
}
