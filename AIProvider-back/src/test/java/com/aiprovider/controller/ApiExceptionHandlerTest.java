package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.service.quant.BacktestTaskException;
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

    @Test
    void executionCompatibilityFailuresAreBadRequestsAndFixedIdConflictRemainsConflict() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        for (String code :
                new String[] {
                    "BACKTEST_EXECUTION_PROFILE_REQUIRED",
                    "BACKTEST_EXECUTION_PROFILE_NOT_SUPPORTED",
                    "BACKTEST_MARKET_EXECUTION_INCOMPATIBLE",
                    "BACKTEST_STRATEGY_MARKET_INCOMPATIBLE",
                    "BACKTEST_STRATEGY_EXECUTION_INCOMPATIBLE",
                    "BACKTEST_DIRECTION_INCOMPATIBLE",
                    "BACKTEST_ORDER_SIZING_INCOMPATIBLE",
                    "BACKTEST_MARKET_FEATURE_MISSING"
                }) {
            assertEquals(
                    400,
                    handler.backtest(new BacktestTaskException(code, "invalid"))
                            .getStatusCodeValue(),
                    code);
        }
        assertEquals(
                409,
                handler.backtest(
                                new BacktestTaskException(
                                        "BACKTEST_RUN_ID_CONFLICT", "different request"))
                        .getStatusCodeValue());
    }
}
