package com.heima.smartauth.Service.impl;

import com.heima.smartauth.Mapper.UserMapper;

import com.heima.smartauth.Service.UserService;
import com.heima.smartauth.Utils.JwtUtils;
import com.heima.smartauth.entity.AuthUser;
import com.heima.smartauth.entity.SysUserAuth;

import com.heima.smartcommon.Context.BaseContext;
import com.heima.smartcommon.DTO.LoginDTO;
import com.heima.smartcommon.DTO.RegisterDTO;
import com.heima.smartcommon.Exception.BusinessException;
import com.heima.smartcommon.Result.CommonResult;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    @Autowired
    private RedisTemplate redisTemplate;

    public UserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
    @Override
    @Transactional
    public CommonResult<Map<String,Object>> login(LoginDTO loginDTO) {
        if(loginDTO.getUsername()==null){
            throw new BusinessException("用户名不能为空");
        }
        if(loginDTO.getPassword()==null){
            throw new BusinessException("密码不能为空");
        }
        AuthUser user = userMapper.findByUsername(loginDTO.getUsername());
        if(user==null){
            return  CommonResult.error("用户不存在");
        }
        if(!passwordEncoder.matches(loginDTO.getPassword(),user.getPasswordHash())){
            return  CommonResult.error("密码错误");
        }

        Map<String,Object> data = new HashMap<>();
        Long id= userMapper.GetUserId(user.getUsername());
        //存threadlocal
        BaseContext.setCurrentId(id);
        String token = new JwtUtils().generateToken(user.getUsername(), user.getRole(),id);
        Map<String,Object> userData = new HashMap<>();
        userData.put("id", id);
        userData.put("token", token);
        AuthUser user1 = userMapper.findById(id);
        if("AGENT".equals(user1.getRole())){
            Long agentId = id;
            redisTemplate.opsForValue()
                    .set("agent:heartbeat:"+agentId,
                            "1",
                            60,
                            TimeUnit.SECONDS);

            redisTemplate.opsForZSet()
                    .add("agent_load",agentId,0);
        }
        return CommonResult.success(userData);

    }

    @Override
    @Transactional
    public AuthUser findByUserId(String userId) {
       return userMapper.findByUserId(userId);
    }

    @Override
    @Transactional
    public CommonResult< Map<String,Object>> register(RegisterDTO logindto) {
        if(logindto.getUsername()==null){
            throw new BusinessException("用户名不能为空");
        }
        if(logindto.getPassword()==null){
            throw new BusinessException("密码不能为空");
        }
        if(logindto.getRole()==null){
            throw new BusinessException("角色不能为空");
        }
        AuthUser user1 = userMapper.findByUsername(logindto.getUsername());
        if(user1!=null){
            return  CommonResult.error("用户已存在");
        }
        AuthUser user = new AuthUser();
        user.setUsername(logindto.getUsername());
        String password = passwordEncoder.encode(logindto.getPassword());
        user.setPasswordHash(password);
        user.setRole(logindto.getRole());
        user.setCreatedAt(LocalDateTime.now());
        Long id=  userMapper.insert(user);
        userMapper.insertAuth(user.getId(), "password", logindto.getUsername(), logindto.getPassword());
        //存threadlocal
        BaseContext.setCurrentId(id);
        //构建token
        String token = new JwtUtils().generateToken(user.getUsername(), user.getRole(),id);
        Map<String,Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", id);
        return CommonResult.success(data);

    }

    @Override
    public SysUserAuth findByGithubId(String githubId) {
      return  userMapper.findByGithubId(githubId);
    }

    @Override
    @Transactional
    public SysUserAuth registerGithubUser(String githubId, String username, String avatar) {
       return userMapper.registerGithubUser(githubId, username, avatar);
    }

    @Override
    @Transactional
    public AuthUser save(AuthUser user) {
       userMapper.insert(user);
        return user;
    }

    @Override
    @Transactional
    public void saveAuth(Long id, String github, String githubId, Object o) {
        userMapper.insertAuth(id, github, githubId, o);
    }

    @Override
    public AuthUser findById(Long userId) {
       AuthUser user = userMapper.findById(userId);
       return user;
    }

    @Override
    public AuthUser findByUsername(String username) {
       return userMapper.findByUsername(username);
    }


}
