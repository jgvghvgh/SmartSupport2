package com.heima.smartauth.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SysUserAuth {
    private Long id;             // 主键
    private Long userId;         // 关联 SysUser.id
    private String identityType; // 登录类型: password / github / wechat
    private String identifier;   // 用户名 / GitHub ID / 微信 openid
    private String credential;   // 密码 / token / 空
    private LocalDateTime createTime;
}
