package com.aiprovider.quant.exchange.binance.usdm;

/**
 * Binance USDⓈ-M Futures 上游异常。
 *
 * 封装 Binance 公共行情上游返回的 HTTP 状态码、业务错误码、错误消息、Retry-After 头
 * 与 X-MBX-USED-WEIGHT-1M 头。由 {@link BinanceUsdmPublicMarketClient} 在上游失败时抛出，
 * 不重试、不降级、不伪造成功。
 *
 * httpStatus 约定：
 * <ul>
 *   <li>0：DNS 解析或连接失败</li>
 *   <li>-1：请求超时</li>
 *   <li>4xx/5xx：上游真实 HTTP 状态码</li>
 * </ul>
 */
public class BinanceUsdmUpstreamException extends RuntimeException {

    private final int httpStatus;
    private final int errorCode;
    private final String errorMsg;
    private final Integer retryAfter;
    private final String usedWeight1m;

    public BinanceUsdmUpstreamException(int httpStatus, int errorCode, String errorMsg,
                                        Integer retryAfter, String usedWeight1m, Throwable cause) {
        super(buildMessage(httpStatus, errorCode, errorMsg), cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
        this.retryAfter = retryAfter;
        this.usedWeight1m = usedWeight1m;
    }

    public BinanceUsdmUpstreamException(int httpStatus, int errorCode, String errorMsg,
                                        Integer retryAfter, String usedWeight1m) {
        this(httpStatus, errorCode, errorMsg, retryAfter, usedWeight1m, null);
    }

    private static String buildMessage(int httpStatus, int errorCode, String errorMsg) {
        return "Binance USDM 上游失败 httpStatus=" + httpStatus + " errorCode=" + errorCode
                + (errorMsg == null || errorMsg.isEmpty() ? "" : " msg=" + errorMsg);
    }

    public int getHttpStatus() { return httpStatus; }

    public int getErrorCode() { return errorCode; }

    public String getErrorMsg() { return errorMsg; }

    public Integer getRetryAfter() { return retryAfter; }

    public String getUsedWeight1m() { return usedWeight1m; }
}
