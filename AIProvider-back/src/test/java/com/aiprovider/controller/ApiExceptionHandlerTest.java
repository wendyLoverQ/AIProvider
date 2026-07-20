package com.aiprovider.controller;

import com.aiprovider.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerTest {
    @Test
    void translatesKnownAccountStateFailuresIntoActionableResponses() {
        ApiExceptionHandler handler=new ApiExceptionHandler();

        ResponseEntity<Result<Void>> disabled=handler.illegalState(new IllegalStateException("ACCOUNT_DISABLED"));
        ResponseEntity<Result<Void>> inUse=handler.illegalState(new IllegalStateException("ACCOUNT_IN_USE"));

        assertEquals(409,disabled.getStatusCodeValue());
        assertEquals("账号已停用，请先编辑账号并启用",disabled.getBody().getMessage());
        assertEquals(409,inUse.getStatusCodeValue());
        assertEquals("账号仍被业务模块使用，不能删除",inUse.getBody().getMessage());
    }
}
