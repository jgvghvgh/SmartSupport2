package com.heima.smartauth.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthUser {
    private Long id;
    private String username;
    private String passwordHash;
    private String email;
    private String role;
    private String avatar;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
