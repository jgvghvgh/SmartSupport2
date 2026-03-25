package com.heima.smartauth.Service.impl;

import com.heima.smartauth.Service.UserService;
import com.heima.smartauth.entity.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;


import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AuthUser user = userService.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException("用户不存在");
        return new User(user.getUsername(), user.getPasswordHash(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_"+user.getRole() )));
    }
}
