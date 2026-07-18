package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.service.TwitterAutomationException;
import com.aiprovider.service.CryptoMarketUpstreamException;
import com.aiprovider.service.FoundryUnavailableException;
import com.aiprovider.service.RemoteCodexException;
import com.aiprovider.service.ContentAiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> badRequest(IllegalArgumentException exception) { return Result.error(400, exception.getMessage()); }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> uploadTooLarge(MaxUploadSizeExceededException exception) {
        return Result.error(400, "上传文件过大：单张最多 15MB，请求总大小最多 60MB");
    }

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
}
