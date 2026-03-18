package com.heima.smartauth.Controller;

import com.heima.smartauth.Service.Userservice;
;
import com.heima.smartcommon.DTO.LoginDTO;
import com.heima.smartcommon.DTO.RegisterDTO;
import com.heima.smartcommon.Result.CommonResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private Userservice userservice;
    @GetMapping("/test")
    public String test(){
        return "hello world";
    }
    @PostMapping("/login")
    public CommonResult<Map<String,Object>> login(@RequestBody LoginDTO loginDTO){
        return userservice.login(loginDTO);
    }
    @PostMapping("/register")
    public CommonResult< Map<String,Object>> register(@RequestBody RegisterDTO logindto){
        return userservice.register(logindto);
    }

}
