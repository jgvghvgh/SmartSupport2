package com.heima.smartcommon.Exception;

import com.heima.smartcommon.Result.CommonResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public CommonResult<T> handleBusinessException(BusinessException ex) {
        return CommonResult.error(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public CommonResult<T> handleOtherException(Exception ex) {
        ex.printStackTrace(); // 方便调试，可根据环境关闭
        return CommonResult.error("系统内部错误，请联系管理员");
    }
}
