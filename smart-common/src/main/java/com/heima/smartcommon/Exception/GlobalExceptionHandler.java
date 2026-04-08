package com.heima.smartcommon.Exception;

import com.heima.smartcommon.Result.CommonResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public static <T> CommonResult<T> handleBusinessException(BusinessException ex) {
        return CommonResult.error(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public static <T> CommonResult<T> handleOtherException(Exception ex) {
        ex.printStackTrace();
        return CommonResult.error("系统内部错误，请联系管理员");
    }
}
