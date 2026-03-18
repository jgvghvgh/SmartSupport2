package com.heima.smartauth.Mapper;

import com.heima.smartauth.entity.AuthUser;
import com.heima.smartauth.entity.SysUserAuth;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {
    @Insert("insert into user(username,password_hash,email,role,avatar,created_at,updated_at) values(#{username},#{passwordHash},#{email},#{role},#{avatar},#{createdAt},#{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    Long insert(AuthUser user);

    @Select("select * from user_auth where identifier = #{githubId}")
    SysUserAuth findByGithubId(String githubId);
    @Insert("insert into user_auth(user_id,identity_type,identifier,credential) values(#{id},#{identityType},#{identifier},#{credential})")
    void insertAuth(@Param("id") Long id,
                    @Param("identityType") String identityType,
                    @Param("identifier") String identifier,
                    @Param("credential") Object credential);
    @Select("select * from user where id = #{userId}")
    AuthUser findById(Long userId);
    @Select("select * from user where id = #{userId}")
    AuthUser findByUserId(String userId);
    @Select("select * from user where username = #{username}")
    AuthUser findByUsername(String username);
    @Insert("insert into user_auth(identifier,identityType) values(#{githubId},'github')")
    SysUserAuth registerGithubUser(String githubId, String username, String avatar);
    @Select("select id from user where username = #{username}")
    Long GetUserId(String username);
}
