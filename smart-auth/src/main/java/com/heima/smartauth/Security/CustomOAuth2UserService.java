package com.heima.smartauth.Security;


import com.heima.smartauth.Service.UserService;
import com.heima.smartauth.entity.AuthUser;
import com.heima.smartauth.entity.SysUserAuth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    @Autowired
    private  UserService userService;

    public CustomOAuth2UserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!"github".equals(registrationId)) {
            throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
        }
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String githubId = String.valueOf(attributes.get("id"));
        String username = (String) attributes.get("login");
        String avatar = (String) attributes.get("avatar_url");
        // 查询用户是否存在
        SysUserAuth existingAuth = userService.findByGithubId(githubId);
        AuthUser   user;
        if (existingAuth == null) {
            // 用户第一次登录，注册 SysUser + SysUserAuth
            user = new AuthUser();
            user.setUsername(username);
            user.setAvatar(avatar);
            user.setRole("USER");
            userService.save(user);
            Long userId = user.getId();
            userService.saveAuth(userId, "github", githubId, null);
        } else {
            user = userService.findById(existingAuth.getUserId());
        }
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "id"
        );
    }
}
