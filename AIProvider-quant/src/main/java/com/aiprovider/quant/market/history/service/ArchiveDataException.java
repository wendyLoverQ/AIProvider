package com.aiprovider.quant.market.history.service;

/**
 * Binance 官方归档数据异常。
 *
 * 携带稳定错误码，供调用方区分下载失败、校验失败、格式错误等不同场景。
 * 不得统一降级为 ARCHIVE_IMPORT_ERROR。
 */
public class ArchiveDataException extends RuntimeException {

    public static final String ERR_ARCHIVE_NOT_FOUND = "ARCHIVE_NOT_FOUND";
    public static final String ERR_ARCHIVE_DOWNLOAD_FAILED = "ARCHIVE_DOWNLOAD_FAILED";
    public static final String ERR_ARCHIVE_TOO_LARGE = "ARCHIVE_TOO_LARGE";
    public static final String ERR_ARCHIVE_CHECKSUM_MISSING = "ARCHIVE_CHECKSUM_MISSING";
    public static final String ERR_ARCHIVE_CHECKSUM_INVALID = "ARCHIVE_CHECKSUM_INVALID";
    public static final String ERR_ARCHIVE_ZIP_INVALID = "ARCHIVE_ZIP_INVALID";
    public static final String ERR_ARCHIVE_CSV_INVALID = "ARCHIVE_CSV_INVALID";
    public static final String ERR_ARCHIVE_INTERRUPTED = "ARCHIVE_INTERRUPTED";
    public static final String ERR_ARCHIVE_RANGE_NOT_FULLY_AVAILABLE = "ARCHIVE_RANGE_NOT_FULLY_AVAILABLE";

    private final String errorCode;

    public ArchiveDataException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ArchiveDataException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
