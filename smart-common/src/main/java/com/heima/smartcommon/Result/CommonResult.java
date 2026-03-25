package com.heima.smartcommon.Result;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor

public class CommonResult<T> {
    private int code;
    private String message;
    private T data;

    public CommonResult() {
    }

    //增删改 成功响应
    public CommonResult<T> success(){
        return new CommonResult<>(1,"操作成功",null);
    }

    public static <T> CommonResult<T> success(T data){
        return new CommonResult<>(1,"操作成功",data);
    }

    public static <T> CommonResult<T> error(String msg){
        return new CommonResult<>(0,msg,null);
    }


}
