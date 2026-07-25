package com.aiprovider.service.quant;

/**
 * 历史行情同步任务异常。
 *
 * 携带错误码，供 Controller 层转换为 HTTP 状态码和响应体。
 */
public class MarketHistoryTaskException extends RuntimeException {

    private final String errorCode;

    public MarketHistoryTaskException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
