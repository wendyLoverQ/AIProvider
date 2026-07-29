package com.aiprovider.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 为后端业务边界提供统一、无敏感参数的结构化日志。
 *
 * <p>覆盖 Controller、Service、Repository、MyBatis Mapper 和外部适配器的
 * Spring Bean 公开方法。切面只读取显式命名的 ID 参数、集合规模和返回规模，
 * 不序列化 DTO、请求体、Prompt、凭据或文件内容。</p>
 */
@Aspect
@Component
@Order
public class BusinessOperationLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(BusinessOperationLoggingAspect.class);
    private static final int MAX_IDENTIFIER_LENGTH = 160;
    private static final Set<String> READ_PREFIXES;
    private static final Set<String> COUNT_PROPERTIES;

    static {
        Set<String> prefixes = new LinkedHashSet<>();
        Collections.addAll(prefixes,
                "get", "find", "list", "query", "search", "count", "page", "exists",
                "has", "is", "load", "read", "fetch", "resolve", "preview", "status",
                "health", "overview", "history", "latest", "available", "calculate",
                "compute", "parse", "format", "build", "map", "convert", "render");
        READ_PREFIXES = Collections.unmodifiableSet(prefixes);
        Set<String> countProperties = new LinkedHashSet<>();
        Collections.addAll(countProperties,
                "ids", "items", "rows", "records", "tasks", "candidates", "entries", "files");
        COUNT_PROPERTIES = Collections.unmodifiableSet(countProperties);
    }

    @Around(
            "execution(public * com.aiprovider.controller..*(..)) || " +
            "execution(public * com.aiprovider.service..*(..)) || " +
            "execution(public * com.aiprovider.repository..*(..)) || " +
            "execution(public * com.aiprovider.mapper..*(..)) || " +
            "execution(public * com.aiprovider.adapter..*(..))")
    public Object logBusinessOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String layer = layer(method.getDeclaringClass());
        String operation = operation(layer, method);
        boolean readOnly = isReadOnly(method.getName());
        InvocationSummary request = summarizeRequest(method, joinPoint.getArgs());
        long startedAt = System.nanoTime();

        try {
            Object result = joinPoint.proceed();
            long durationMs = elapsedMillis(startedAt);
            ResultSummary response = summarizeResult(method, result, readOnly);
            String message = "operation={} layer={} businessIds={} requestedCount={} " +
                    "{}={} durationMs={} resultStatus=SUCCESS";
            if (readOnly) {
                log.debug(message, operation, layer, request.businessIds, request.requestedCount,
                        response.countField, response.count, durationMs);
            } else {
                log.info(message, operation, layer, request.businessIds, request.requestedCount,
                        response.countField, response.count, durationMs);
            }
            return result;
        } catch (Throwable failure) {
            long durationMs = elapsedMillis(startedAt);
            log.error("operation={} layer={} businessIds={} requestedCount={} affectedRows=unknown " +
                            "durationMs={} resultStatus=FAILED errorType={} failureLocation={}",
                    operation, layer, request.businessIds, request.requestedCount, durationMs,
                    failure.getClass().getSimpleName(), failureLocation(failure));
            throw failure;
        }
    }

    private static InvocationSummary summarizeRequest(Method method, Object[] arguments) {
        Parameter[] parameters = method.getParameters();
        List<String> identifiers = new ArrayList<>();
        int requestedCount = -1;

        for (int index = 0; index < arguments.length; index++) {
            Object argument = arguments[index];
            requestedCount = Math.max(requestedCount, requestCountOf(argument));
            if (index >= parameters.length) {
                continue;
            }
            String parameterName = parameters[index].getName();
            if (isIdentifierName(parameterName)) {
                appendIdentifiers(identifiers, parameterName, argument);
            }
            if (isApplicationPayload(argument)) {
                requestedCount = Math.max(requestedCount,
                        summarizePayload(argument, identifiers));
            }
        }
        return new InvocationSummary(identifiers.isEmpty() ? "none" : identifiers.toString(),
                requestedCount < 0 ? 1 : requestedCount);
    }

    private static ResultSummary summarizeResult(Method method, Object result, boolean readOnly) {
        Object value = unwrapResponse(result);
        if (value == null) {
            return new ResultSummary("returnedCount", 0);
        }
        if (!readOnly && value instanceof Number && returnsAffectedRows(method)) {
            return new ResultSummary("affectedRows", ((Number) value).longValue());
        }
        if (value instanceof Number) {
            return new ResultSummary("returnedValue", ((Number) value).longValue());
        }
        if (value instanceof Boolean) {
            return new ResultSummary("returnedValue", Boolean.TRUE.equals(value) ? 1 : 0);
        }
        return new ResultSummary("returnedCount", sizeOf(value));
    }

    private static boolean returnsAffectedRows(Method method) {
        String normalized = method.getName().toLowerCase(Locale.ROOT);
        return normalized.startsWith("update") || normalized.startsWith("delete") ||
                normalized.startsWith("remove") || normalized.startsWith("trash") ||
                normalized.startsWith("restore") || normalized.startsWith("mark") ||
                normalized.startsWith("clear") || normalized.startsWith("clean") ||
                normalized.startsWith("purge") || normalized.startsWith("archive") ||
                normalized.startsWith("upsert") || normalized.startsWith("assign") ||
                (normalized.startsWith("insert") &&
                        (method.getReturnType() == int.class || method.getReturnType() == Integer.class));
    }

    private static Object unwrapResponse(Object value) {
        if (value instanceof ResponseEntity) {
            return unwrapResponse(((ResponseEntity<?>) value).getBody());
        }
        if (value instanceof Optional) {
            return unwrapResponse(((Optional<?>) value).orElse(null));
        }
        if (value != null && "com.aiprovider.common.Result".equals(value.getClass().getName())) {
            return unwrapResponse(invokeAccessor(value, "getData"));
        }
        return value;
    }

    private static int requestCountOf(Object value) {
        if (value instanceof Collection) {
            return ((Collection<?>) value).size();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).size();
        }
        if (value != null && value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return payloadCollectionSize(value);
    }

    private static int sizeOf(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).size();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).size();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value);
        }
        int payloadCount = payloadCollectionSize(value);
        if (payloadCount >= 0) {
            return payloadCount;
        }
        return 1;
    }

    private static void appendIdentifiers(List<String> target, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                appendIdentifier(target, name, item);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                appendIdentifier(target, name, Array.get(value, index));
            }
            return;
        }
        appendIdentifier(target, name, value);
    }

    private static void appendIdentifier(List<String> target, String name, Object value) {
        if (!isSafeIdentifierValue(value)) {
            return;
        }
        String text = sanitizeIdentifier(String.valueOf(value));
        if (!text.isEmpty()) {
            target.add(name + "=" + text);
        }
    }

    private static boolean isSafeIdentifierValue(Object value) {
        return value instanceof Number || value instanceof UUID || value instanceof CharSequence ||
                value instanceof Enum;
    }

    private static String sanitizeIdentifier(String value) {
        StringBuilder sanitized = new StringBuilder(Math.min(value.length(), MAX_IDENTIFIER_LENGTH));
        for (int index = 0; index < value.length() && sanitized.length() < MAX_IDENTIFIER_LENGTH; index++) {
            char current = value.charAt(index);
            if (Character.isLetterOrDigit(current) || current == '-' || current == '_' ||
                    current == ':' || current == '.' || current == '/') {
                sanitized.append(current);
            } else {
                sanitized.append('_');
            }
        }
        return sanitized.toString();
    }

    private static boolean isIdentifierName(String parameterName) {
        String normalized = parameterName.toLowerCase(Locale.ROOT);
        return !isSensitiveName(normalized) &&
                (parameterName.equals("id") || parameterName.equals("ids") ||
                parameterName.endsWith("Id") || parameterName.endsWith("Ids") ||
                normalized.endsWith("_id") || normalized.endsWith("_ids"));
    }

    private static boolean isSensitiveName(String normalizedName) {
        return normalizedName.contains("secret") || normalizedName.contains("token") ||
                normalizedName.contains("session") || normalizedName.contains("credential") ||
                normalizedName.contains("password") || normalizedName.contains("auth") ||
                normalizedName.contains("cookie") || normalizedName.contains("key");
    }

    private static int summarizePayload(Object payload, List<String> identifiers) {
        int requestedCount = -1;
        for (Method accessor : payload.getClass().getMethods()) {
            if (accessor.getParameterCount() != 0 || !accessor.getName().startsWith("get") ||
                    "getClass".equals(accessor.getName())) {
                continue;
            }
            String property = propertyName(accessor.getName());
            if (!isIdentifierName(property) && !COUNT_PROPERTIES.contains(property.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Object value = invokeAccessor(payload, accessor);
            if (isIdentifierName(property)) {
                appendIdentifiers(identifiers, property, value);
            }
            if (COUNT_PROPERTIES.contains(property.toLowerCase(Locale.ROOT))) {
                requestedCount = Math.max(requestedCount, requestCountOf(value));
            }
        }
        return requestedCount;
    }

    private static int payloadCollectionSize(Object payload) {
        if (!isApplicationPayload(payload)) {
            return -1;
        }
        for (Method accessor : payload.getClass().getMethods()) {
            if (accessor.getParameterCount() != 0 || !accessor.getName().startsWith("get")) {
                continue;
            }
            String property = propertyName(accessor.getName()).toLowerCase(Locale.ROOT);
            if (!COUNT_PROPERTIES.contains(property)) {
                continue;
            }
            Object value = invokeAccessor(payload, accessor);
            if (value instanceof Collection || value instanceof Map ||
                    (value != null && value.getClass().isArray())) {
                return sizeOf(value);
            }
        }
        return -1;
    }

    private static boolean isApplicationPayload(Object value) {
        if (value == null || value.getClass().getPackage() == null) {
            return false;
        }
        String packageName = value.getClass().getPackageName();
        return packageName.startsWith("com.aiprovider.model.") ||
                packageName.startsWith("com.aiprovider.controller.");
    }

    private static String propertyName(String accessorName) {
        if (accessorName.length() <= 3) {
            return "";
        }
        String raw = accessorName.substring(3);
        return Character.toLowerCase(raw.charAt(0)) + raw.substring(1);
    }

    private static Object invokeAccessor(Object target, String accessorName) {
        try {
            return invokeAccessor(target, target.getClass().getMethod(accessorName));
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object invokeAccessor(Object target, Method accessor) {
        try {
            return accessor.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isReadOnly(String methodName) {
        String normalized = methodName.toLowerCase(Locale.ROOT);
        for (String prefix : READ_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String layer(Class<?> declaringType) {
        String packageName = declaringType.getPackageName();
        if (packageName.contains(".controller")) {
            return "CONTROLLER";
        }
        if (packageName.contains(".service")) {
            return "SERVICE";
        }
        if (packageName.contains(".repository")) {
            return "REPOSITORY";
        }
        if (packageName.contains(".mapper")) {
            return "MAPPER";
        }
        return "ADAPTER";
    }

    private static String operation(String layer, Method method) {
        return layer.toLowerCase(Locale.ROOT) + "." +
                method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static String failureLocation(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        for (StackTraceElement frame : root.getStackTrace()) {
            if (frame.getClassName().startsWith("com.aiprovider.")) {
                return frame.getClassName() + ":" + frame.getLineNumber();
            }
        }
        return root.getClass().getSimpleName();
    }

    private static final class InvocationSummary {
        private final String businessIds;
        private final int requestedCount;

        private InvocationSummary(String businessIds, int requestedCount) {
            this.businessIds = businessIds;
            this.requestedCount = requestedCount;
        }
    }

    private static final class ResultSummary {
        private final String countField;
        private final long count;

        private ResultSummary(String countField, long count) {
            this.countField = countField;
            this.count = count;
        }
    }
}
