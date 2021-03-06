package com.wenjun.seckill.enums;

import com.wenjun.seckill.error.CommonError;

/**
 * @Author: wenjun
 * @Date: 2019/12/19 15:28
 */
public enum EmBusinessError implements CommonError {
    PARAMETER_VALIDATION_ERROR(10001,"参数不合法"),
    UNKNOWN_ERROR(10002,"未知错误"),
    USER_NOT_EXIT(20001,"用户不存在"),
    USER_LOGIN_FAIL(20002,"用户或密码不正确"),
    USER_NOT_LOGIN(20003,"用户还未登录"),
    FIND_PASSWORD_FAIL(20004,"找回密码失败"),
    STOCK_NOT_ENOUGH(30001,"库存不足"),
    MQ_SENT_FAIL(30002,"库存异步消息投放失败"),
    RATE_LIMIT(30003,"活动太火爆，请稍后再试"),
    ;

    private int errCode;
    private String errMsg;

    EmBusinessError(int errCode, String errMsg) {
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    @Override
    public int getErrCode() {
        return this.errCode;
    }

    @Override
    public String getErrMsg() {
        return this.errMsg;
    }

    @Override
    public CommonError setErrMsg(String errMsg) {
        this.errMsg = errMsg;
        return this;
    }
}
