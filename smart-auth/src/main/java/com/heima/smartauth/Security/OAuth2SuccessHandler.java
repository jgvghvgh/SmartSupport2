package com.heima.smartauth.Security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.smartauth.Service.Userservice;
import com.heima.smartauth.Utils.JwtUtils;
import com.heima.smartauth.entity.AuthUser;
import com.heima.smartauth.entity.SysUserAuth;
import com.heima.smartcommon.Context.BaseContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;           // 你生成 JWT 的工具类
    private final Userservice userService;      // 用户服务

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        // authentication.getPrincipal() 是 OAuth2User
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        // 从 OAuth2User 获取 GitHub 信息
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String githubId = String.valueOf(attributes.get("id"));       // GitHub 唯一 ID
        String username = (String) attributes.get("login");          // GitHub 用户名
        String avatar = (String) attributes.get("avatar_url");       // GitHub 头像

        // 查询用户是否存在
        SysUserAuth existingAuth = userService.findByGithubId(githubId);
        AuthUser  user = userService.findById(existingAuth.getUserId());
        // 存 ThreadLocal
        BaseContext.setCurrentId(user.getId());
        // 生成 JWT
        String token = jwtUtils.generateToken(String.valueOf(user.getUsername()), user.getRole(),user.getId());
        // 返回给前端
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());

        response.setContentType("application/json;charset=UTF-8");
        // 使用 JSON 序列化
        response.getWriter().write(new ObjectMapper().writeValueAsString(data));
    }
}