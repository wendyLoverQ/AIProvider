package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.controller.quant.ResourceNotFoundException;
import com.aiprovider.service.TwitterAutomationException;
import com.aiprovider.service.CryptoMarketUpstreamException;
import com.aiprovider.service.FoundryUnavailableException;
import com.aiprovider.service.RemoteCodexException;
import com.aiprovider.service.ContentAiException;
import com.aiprovider.service.ContentSourceException;
import com.aiprovider.service.XiaohongshuAutomationException;
import com.aiprovider.service.PromptTranslationException;
import com.aiprovider.service.quant.MarketHistoryTaskException;
import com.aiprovider.service.quant.BacktestTaskException;
import com.aiprovider.quant.exchange.binance.usdm.BinanceUsdmUpstreamException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Map<String, StateError> STATE_ERRORS;
    static {
        Map<String,StateError> errors=new HashMap<>();
        errors.put("ACCOUNT_DISABLED",new StateError(409,"账号已停用，请先编辑账号并启用"));
        errors.put("ACCOUNT_IN_USE",new StateError(409,"账号仍被业务模块使用，不能删除"));
        errors.put("CREDENTIAL_MISSING",new StateError(409,"账号尚未配置所需登录凭据"));
        errors.put("CREDENTIAL_EXPIRED",new StateError(409,"账号登录凭据已失效，请重新配置"));
        errors.put("ACCOUNT_NOT_FOUND",new StateError(404,"账号不存在或已经删除"));
        errors.put("PLATFORM_MISMATCH",new StateError(400,"账号平台与当前操作不匹配"));
        errors.put("ACCOUNT_HANDLE_MISSING",new StateError(400,"账号 Handle 尚未配置"));
        errors.put("PUBLIC_CONFIG_INVALID",new StateError(400,"账号公开配置格式不正确"));
        errors.put("LOGIN_TIMEOUT",new StateError(408,"扫码登录已超时，请重新发起"));
        errors.put("PLATFORM_RISK_CONTROL",new StateError(502,"平台要求额外安全验证，请稍后重试"));
        errors.put("ADAPTER_UNAVAILABLE",new StateError(503,"当前平台连接器暂不可用"));
        STATE_ERRORS=Collections.unmodifiableMap(errors);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> badRequest(IllegalArgumentException exception) { return Result.error(400, exception.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> validation(MethodArgumentNotValidException exception) {
        return Result.error(400, "请求参数校验失败");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> notFound(ResourceNotFoundException exception) { return Result.error(404, exception.getMessage()); }

    @ExceptionHandler(TwitterAutomationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Result<Void> twitterAutomation(TwitterAutomationException exception) {
        return Result.error(502, exception.getMessage());
    }

    @ExceptionHandler(CryptoMarketUpstreamException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Result<Void> cryptoMarketUpstream(CryptoMarketUpstreamException exception) {
        return Result.error(502, exception.getMessage());
    }

    @ExceptionHandler(BinanceUsdmUpstreamException.class)
    public ResponseEntity<Result<Void>> binanceUsdmUpstream(BinanceUsdmUpstreamException exception) {
        int upstream = exception.getHttpStatus();
        int status;
        String message;
        if (upstream == 429 || upstream == 418) {
            status = 429;
            message = "Binance 行情上游限频，请稍后重试";
        } else if (upstream >= 500) {
            status = 502;
            message = "Binance 行情上游暂时不可用，请稍后重试";
        } else {
            status = 502;
            message = "Binance 行情上游请求失败，请稍后重试";
        }
        return ResponseEntity.status(status).body(Result.error(status, message));
    }

    @ExceptionHandler(FoundryUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> foundryUnavailable(FoundryUnavailableException exception) {
        return Result.error(503, exception.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> unauthorized(SecurityException exception) { return Result.error(401, exception.getMessage()); }

    @ExceptionHandler(RemoteCodexException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> remoteCodex(RemoteCodexException exception) { return Result.error(503, exception.getMessage()); }

    @ExceptionHandler(ContentAiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Result<Void> contentAi(ContentAiException exception) { return Result.error(502, exception.getMessage()); }

    @ExceptionHandler(ContentSourceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Result<Void> contentSource(ContentSourceException exception) { return Result.error(502, exception.getMessage()); }

    @ExceptionHandler(XiaohongshuAutomationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Result<Void> xiaohongshuAutomation(XiaohongshuAutomationException exception) { return Result.error(502, exception.getMessage()); }

    @ExceptionHandler(PromptTranslationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Result<Void> promptTranslation(PromptTranslationException exception) { return Result.error(502, exception.getMessage()); }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result<Void>> illegalState(IllegalStateException exception) {
        StateError error=STATE_ERRORS.get(exception.getMessage());
        if(error==null)error=new StateError(500,"服务器状态异常，请稍后重试");
        return ResponseEntity.status(error.status).body(Result.error(error.status,error.message));
    }

    @ExceptionHandler(MarketHistoryTaskException.class)
    public ResponseEntity<Result<Void>> marketHistoryTask(MarketHistoryTaskException exception) {
        int status;
        switch (exception.getErrorCode()) {
            case "DATASET_ALREADY_SYNCING":
                status = 409;
                break;
            case "EXECUTOR_QUEUE_FULL":
                status = 503;
                break;
            case "DATASET_CREATE_FAILED":
                status = 500;
                break;
            case "CONTRACT_NOT_FOUND":
                status = 404;
                break;
            default:
                status = 400;
                break;
        }
        return ResponseEntity.status(status).body(Result.error(status, exception.getMessage()));
    }

    @ExceptionHandler(BacktestTaskException.class)
    public ResponseEntity<Result<Void>> backtest(BacktestTaskException exception) {
        int status;
        switch (exception.getErrorCode()) {
            case "BACKTEST_RUN_NOT_FOUND": case "BACKTEST_DATASET_NOT_FOUND": case "BACKTEST_EXPERIMENT_NOT_FOUND": status=404; break;
            case "BACKTEST_QUEUE_FULL": status=503; break;
            case "BACKTEST_STATE_CONFLICT": case "BACKTEST_EXPERIMENT_STATE_CONFLICT": case "BACKTEST_RUN_ID_CONFLICT": status=409; break;
            case "BACKTEST_EXPERIMENT_DISPATCH_FAILED": status=500; break;
            case "BACKTEST_RESULT_INVALID": case "BACKTEST_EXECUTION_FAILED": case "BACKTEST_PERSISTENCE_FAILED": status=500; break;
            default: status=400;
        }
        return ResponseEntity.status(status).body(Result.error(status, exception.getMessage()));
    }

    private static final class StateError {
        private final int status;private final String message;
        private StateError(int status,String message){this.status=status;this.message=message;}
    }
}
