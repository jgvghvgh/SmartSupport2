package com.heima.smartticket.Mapper;

import com.heima.smartauth.entity.AuthUser;
import com.heima.smartauth.entity.SysUserAuth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommonUserMapper {
    @Select("select * from user_auth where identifier = #{githubId}")
    SysUserAuth findByGithubId(String githubId);
    @Select("select * from user where id = #{userId}")
    AuthUser findById(Long userId);
    @Select("select * from user where id = #{userId}")
    AuthUser findByUserId(String userId);
    @Select("select * from user where username = #{username}")
    AuthUser findByUsername(String username);
}
