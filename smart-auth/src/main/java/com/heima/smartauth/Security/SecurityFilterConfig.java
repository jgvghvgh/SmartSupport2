package com.heima.smartauth.Security;


import com.heima.smartauth.Filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityFilterConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1️ 关闭 CSRF
                .csrf(csrf -> csrf.disable())

                // 2️ 路由授权
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/user/login",     // 自定义登录接口
                                "/api/user/register",  // 注册接口
                                "/oauth2/**",          // OAuth2 授权相关路径
                                "/login/oauth2/**"
                        ).permitAll()
                        .anyRequest().authenticated() // 其他都需要登录
                )

                // 3️ 启用 OAuth2 登录
                .oauth2Login(oauth -> oauth
                        .loginPage("/oauth2/authorization/github") // 未登录时自动跳转到 GitHub 登录页
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService)) // 自定义用户信息处理
                        .successHandler(oAuth2SuccessHandler) // 登录成功回调
                )

                // 4  ⃣ 未认证时的行为（默认重定向）
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/github")
                        )
                );

        return http.build();
    }



    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

