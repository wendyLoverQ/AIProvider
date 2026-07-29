package com.aiprovider.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * 供具体业务方法显式记录业务开始和真实返回结果。
 *
 * <p>参数摘要复用统一切面的敏感字段保护，只输出业务 ID 和集合数量，
 * 不序列化 DTO、Prompt、凭据、令牌或文件内容。</p>
 */
public final class BusinessOperationLogger {

    private static final Logger log = LoggerFactory.getLogger(BusinessOperationLogger.class);

    private BusinessOperationLogger() {
    }

    public static void start(String operation, String[] parameterNames, Object[] arguments) {
        BusinessOperationLoggingAspect.InvocationSummary request =
                BusinessOperationLoggingAspect.summarizeRequest(parameterNames, arguments);
        String message = "operation={} stage=BUSINESS_START businessIds={} requestedCount={}";
        if (BusinessOperationLoggingAspect.isReadOnly(methodName(operation))) {
            log.debug(message, operation, request.businessIds, request.requestedCount);
        } else {
            log.info(message, operation, request.businessIds, request.requestedCount);
        }
    }

    public static <T> T success(String operation, T result) {
        String methodName = methodName(operation);
        boolean readOnly = BusinessOperationLoggingAspect.isReadOnly(methodName);
        ResultCount resultCount = summarize(result, methodName, readOnly);
        String message = "operation={} stage=BUSINESS_RESULT {}={} resultStatus=SUCCESS";
        if (readOnly) {
            log.debug(message, operation, resultCount.field, resultCount.value);
        } else {
            log.info(message, operation, resultCount.field, resultCount.value);
        }
        return result;
    }

    private static ResultCount summarize(Object result, String methodName, boolean readOnly) {
        if (result == null) {
            return new ResultCount("returnedCount", 0);
        }
        if (!readOnly && result instanceof Integer && returnsAffectedRows(methodName)) {
            return new ResultCount("affectedRows", ((Integer) result).longValue());
        }
        if (result instanceof Number) {
            return new ResultCount("returnedValue", ((Number) result).longValue());
        }
        if (result instanceof Boolean) {
            return new ResultCount("returnedValue", Boolean.TRUE.equals(result) ? 1 : 0);
        }
        if (result instanceof Collection) {
            return new ResultCount("returnedCount", ((Collection<?>) result).size());
        }
        if (result instanceof Map) {
            return new ResultCount("returnedCount", ((Map<?, ?>) result).size());
        }
        if (result.getClass().isArray()) {
            return new ResultCount("returnedCount", Array.getLength(result));
        }
        return new ResultCount("returnedCount", 1);
    }

    private static boolean returnsAffectedRows(String methodName) {
        String normalized = methodName.toLowerCase(Locale.ROOT);
        return normalized.startsWith("update") || normalized.startsWith("delete") ||
                normalized.startsWith("remove") || normalized.startsWith("trash") ||
                normalized.startsWith("restore") || normalized.startsWith("mark") ||
                normalized.startsWith("clear") || normalized.startsWith("clean") ||
                normalized.startsWith("purge") || normalized.startsWith("archive") ||
                normalized.startsWith("upsert") || normalized.startsWith("assign") ||
                normalized.startsWith("insert");
    }

    private static String methodName(String operation) {
        int separator = operation.lastIndexOf('.');
        return separator < 0 ? operation : operation.substring(separator + 1);
    }

    private static final class ResultCount {
        private final String field;
        private final long value;

        private ResultCount(String field, long value) {
            this.field = field;
            this.value = value;
        }
    }
}
