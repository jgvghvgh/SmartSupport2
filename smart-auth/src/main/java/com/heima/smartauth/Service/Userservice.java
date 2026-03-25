package com.heima.smartauth.Service;

import com.heima.smartauth.entity.AuthUser;
import com.heima.smartauth.entity.SysUserAuth;
import com.heima.smartcommon.DTO.LoginDTO;
import com.heima.smartcommon.DTO.RegisterDTO;
import com.heima.smartcommon.Result.CommonResult;

import java.util.Map;

public interface UserService {
    CommonResult<Map<String,Object>> login(LoginDTO loginDTO);

    AuthUser findByUserId(String userId);

    CommonResult< Map<String,Object>> register(RegisterDTO logindto);


    SysUserAuth findByGithubId(String githubId);

    SysUserAuth registerGithubUser(String githubId, String username, String avatar);

    AuthUser save(AuthUser user);

    void saveAuth(Long id, String github, String githubId, Object o);

    AuthUser findById(Long userId);

    AuthUser findByUsername(String username);
}
