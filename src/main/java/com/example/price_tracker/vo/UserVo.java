package com.example.price_tracker.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.example.price_tracker.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "User profile information data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVo {

    @Schema(description = "User ID", example = "1001")
    private Long id;

    @Schema(description = "Username", example = "john_doe")
    private String username;

    private String email;

    private String nickname;

    @Schema(description = "Role of the user (USER/ADMIN)", example = "USER")
    private UserRole role;

    @Schema(description = "User account status: 1 active, 0 disabled", example = "1")
    private Integer status;

    @Schema(description = "Account creation time")
    private LocalDateTime createdAt;

    @Schema(description = "Account update time")
    private LocalDateTime updatedAt;
}
