package com.wenjie.notice.pojo;

public class Result {
    //(true, StatusCode.OK, "查询成功", countMap)
    private boolean isSuccess;
    private Integer statusCode;
    private String msg;
    private Object data;

    public Result(boolean isSuccess, Integer statusCode, String msg, Object data) {
        this.isSuccess = isSuccess;
        this.statusCode = statusCode;
        this.msg = msg;
        this.data = data;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
