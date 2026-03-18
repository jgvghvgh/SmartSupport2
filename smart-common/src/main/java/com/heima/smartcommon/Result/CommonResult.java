package com.heima.smartcommon.Result;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data

public class CommonResult<T> {
    private int code;
    private String message;
    private Object data;
    public CommonResult(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    public CommonResult(int code, String message) {
        this.code = code;
        this.message = message;
    }
    public CommonResult(Object data) {
        this.data = data;
    }
    //增删改 成功响应
    public CommonResult<T> success(){
        return new CommonResult<T>(1,"操作成功",null);
    }

    public static CommonResult<T> success(Object data){
        return new CommonResult<T>(1,"操作成功",data);
    }

    public static CommonResult<T> error(String msg){
        return new CommonResult<T>(0,msg,null);
    }


}
