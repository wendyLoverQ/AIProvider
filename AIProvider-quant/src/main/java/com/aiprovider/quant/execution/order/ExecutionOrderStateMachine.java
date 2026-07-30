package com.aiprovider.quant.execution.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ExecutionOrderStateMachine {
    public static final String EXECUTION_ORDER_RESTORE_INVALID = "EXECUTION_ORDER_RESTORE_INVALID";
    public static final String EXECUTION_ORDER_RESTORE_MISMATCH = "EXECUTION_ORDER_RESTORE_MISMATCH";

    public ExecutionOrderSnapshot restore(ExecutionOrderRestoreRequest request) {
        validateRestoreRequest(request);
        validateRestoreShape(request);
        try {
            ExecutionOrderSnapshot replayed = create(request.getRequest());
            switch (request.getStatus()) {
                case CREATED:
                    break;
                case ACCEPTED:
                    replayed = accept(replayed, request.getAcceptedAt());
                    break;
                case SUBMITTED:
                    replayed = accept(replayed, request.getAcceptedAt());
                    replayed = submit(replayed, request.getExecutionOrderId(), request.getSubmittedAt());
                    break;
                case PARTIALLY_FILLED:
                case FILLED:
                    replayed = replaySubmittedAndFills(request, replayed);
                    break;
                case CANCELED:
                    replayed = replaySubmittedAndFills(request, replayed);
                    replayed = cancel(replayed, request.getCompletedAt());
                    break;
                case REJECTED:
                    if (request.getAcceptedAt() != null) {
                        replayed = accept(replayed, request.getAcceptedAt());
                    }
                    replayed = reject(replayed, request.getTerminalErrorCode(),
                            request.getTerminalErrorMessage(), request.getCompletedAt());
                    break;
                case FAILED:
                    replayed = accept(replayed, request.getAcceptedAt());
                    if (request.getSubmittedAt() != null) {
                        replayed = submit(replayed, request.getExecutionOrderId(), request.getSubmittedAt());
                        for (ExecutionFill fill : request.getFills()) {
                            replayed = applyFill(replayed, fill);
                        }
                    }
                    replayed = fail(replayed, request.getTerminalErrorCode(),
                            request.getTerminalErrorMessage(), request.getCompletedAt());
                    break;
                default:
                    throw restoreFailure("unsupported status=" + request.getStatus());
            }
            requireRestoredFieldsMatch(replayed, request);
            return replayed;
        } catch (ExecutionOrderException exception) {
            if (EXECUTION_ORDER_RESTORE_INVALID.equals(exception.getErrorCode())
                    || EXECUTION_ORDER_RESTORE_MISMATCH.equals(exception.getErrorCode())) {
                throw exception;
            }
            throw new ExecutionOrderException(EXECUTION_ORDER_RESTORE_INVALID,
                    "execution order state replay failed: " + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new ExecutionOrderException(EXECUTION_ORDER_RESTORE_INVALID,
                    "execution order state replay failed", exception);
        }
    }

    private ExecutionOrderSnapshot replaySubmittedAndFills(
            ExecutionOrderRestoreRequest request, ExecutionOrderSnapshot snapshot) {
        snapshot = accept(snapshot, request.getAcceptedAt());
        snapshot = submit(snapshot, request.getExecutionOrderId(), request.getSubmittedAt());
        for (ExecutionFill fill : request.getFills()) snapshot = applyFill(snapshot, fill);
        return snapshot;
    }

    private void validateRestoreRequest(ExecutionOrderRestoreRequest request) {
        if (request == null) throw restoreFailure("request is null");
        if (request.getRequest() == null) throw restoreFailure("request.request is required");
        if (request.getStatus() == null) throw restoreFailure("status is required");
        if (request.getLastUpdatedAt() == null) throw restoreFailure("lastUpdatedAt is required");
        requireNonNegative("filledQuantity", request.getFilledQuantity());
        requireNonNegative("remainingQuantity", request.getRemainingQuantity());
        requireNonNegative("cumulativeFee", request.getCumulativeFee());
        if (request.getFills() == null) throw restoreFailure("fills is required");
        Set<String> fillIds = new HashSet<>();
        for (int index = 0; index < request.getFills().size(); index++) {
            ExecutionFill fill = request.getFills().get(index);
            if (fill == null) throw restoreFailure("fills[" + index + "] is null");
            if (!fillIds.add(fill.getFillId())) throw restoreFailure("fills contains duplicate fillId");
        }
    }

    private void validateRestoreShape(ExecutionOrderRestoreRequest request) {
        ExecutionOrderStatus status = request.getStatus();
        boolean hasFills = !request.getFills().isEmpty();
        switch (status) {
            case CREATED:
                requireNull("executionOrderId", request.getExecutionOrderId());
                requireNull("acceptedAt", request.getAcceptedAt());
                requireNull("submittedAt", request.getSubmittedAt());
                requireEmptyFills(request);
                requireNull("completedAt", request.getCompletedAt());
                requireNoTerminalError(request);
                break;
            case ACCEPTED:
                requirePresent("acceptedAt", request.getAcceptedAt());
                requireNull("submittedAt", request.getSubmittedAt());
                requireNull("executionOrderId", request.getExecutionOrderId());
                requireEmptyFills(request);
                requireNull("completedAt", request.getCompletedAt());
                requireNoTerminalError(request);
                break;
            case SUBMITTED:
                requireSubmittedFields(request);
                requireEmptyFills(request);
                requireNull("completedAt", request.getCompletedAt());
                requireNoTerminalError(request);
                break;
            case PARTIALLY_FILLED:
                requireSubmittedFields(request);
                if (!hasFills) throw restoreFailure("PARTIALLY_FILLED requires fills");
                if (request.getFilledQuantity().signum() <= 0
                        || request.getRemainingQuantity().signum() <= 0) {
                    throw restoreFailure("PARTIALLY_FILLED requires positive filled and remaining quantities");
                }
                requireNull("completedAt", request.getCompletedAt());
                requireNoTerminalError(request);
                break;
            case FILLED:
                requireSubmittedFields(request);
                if (!hasFills) throw restoreFailure("FILLED requires fills");
                if (request.getRemainingQuantity().signum() != 0) {
                    throw restoreFailure("FILLED requires remainingQuantity=0");
                }
                requirePresent("completedAt", request.getCompletedAt());
                if (!request.getCompletedAt().equals(lastFillTime(request))) {
                    throw restoreFailure("FILLED completedAt must equal the last fill time");
                }
                requireNoTerminalError(request);
                break;
            case CANCELED:
                requireSubmittedFields(request);
                requirePresent("completedAt", request.getCompletedAt());
                requireNoTerminalError(request);
                break;
            case REJECTED:
                requireOptionalAccepted(request);
                requireNull("submittedAt", request.getSubmittedAt());
                requireNull("executionOrderId", request.getExecutionOrderId());
                requireEmptyFills(request);
                requirePresent("completedAt", request.getCompletedAt());
                requireTerminalError(request);
                break;
            case FAILED:
                requirePresent("acceptedAt", request.getAcceptedAt());
                requirePresent("completedAt", request.getCompletedAt());
                requireTerminalError(request);
                if (request.getSubmittedAt() == null) {
                    requireNull("executionOrderId", request.getExecutionOrderId());
                    requireEmptyFills(request);
                } else {
                    requirePresent("executionOrderId", request.getExecutionOrderId());
                }
                break;
            default:
                throw restoreFailure("unsupported status=" + status);
        }
        if (hasFills && (request.getAveragePrice() == null || request.getFeeAsset() == null
                || request.getFeeAsset().isBlank())) {
            throw restoreFailure("filled order requires averagePrice and feeAsset");
        }
        if (!hasFills && (request.getAveragePrice() != null || request.getFeeAsset() != null)) {
            throw restoreFailure("order without fills must not have averagePrice or feeAsset");
        }
        if (request.getSubmittedAt() != null && request.getAcceptedAt() == null) {
            throw restoreFailure("submittedAt requires acceptedAt");
        }
    }

    private void requireSubmittedFields(ExecutionOrderRestoreRequest request) {
        requirePresent("acceptedAt", request.getAcceptedAt());
        requirePresent("submittedAt", request.getSubmittedAt());
        requirePresent("executionOrderId", request.getExecutionOrderId());
    }

    private void requireOptionalAccepted(ExecutionOrderRestoreRequest request) {
        if (request.getAcceptedAt() != null && request.getAcceptedAt().isBefore(request.getRequest().getRequestedAt())) {
            throw restoreFailure("acceptedAt must not precede requestedAt");
        }
    }

    private void requireTerminalError(ExecutionOrderRestoreRequest request) {
        if (blank(request.getTerminalErrorCode()) || blank(request.getTerminalErrorMessage())) {
            throw restoreFailure("terminal error code and message are required");
        }
    }

    private void requireNoTerminalError(ExecutionOrderRestoreRequest request) {
        if (request.getTerminalErrorCode() != null || request.getTerminalErrorMessage() != null) {
            throw restoreFailure("non-terminal order must not have terminal error fields");
        }
    }

    private void requireEmptyFills(ExecutionOrderRestoreRequest request) {
        if (!request.getFills().isEmpty()) throw restoreFailure("status does not allow fills");
    }

    private void requirePresent(String field, Object value) {
        if (value == null) throw restoreFailure(field + " is required");
    }

    private void requireNull(String field, Object value) {
        if (value != null) throw restoreFailure(field + " must be null");
    }

    private void requireNonNegative(String field, BigDecimal value) {
        requirePresent(field, value);
        if (value.signum() < 0) throw restoreFailure(field + " must not be negative");
    }

    private Instant lastFillTime(ExecutionOrderRestoreRequest request) {
        return request.getFills().get(request.getFills().size() - 1).getFilledAt();
    }

    private void requireRestoredFieldsMatch(
            ExecutionOrderSnapshot actual, ExecutionOrderRestoreRequest expected) {
        if (actual.getStatus() != expected.getStatus()
                || !Objects.equals(actual.getExecutionOrderId(), expected.getExecutionOrderId())
                || !decimalEquals(actual.getFilledQuantity(), expected.getFilledQuantity())
                || !decimalEquals(actual.getRemainingQuantity(), expected.getRemainingQuantity())
                || !decimalEquals(actual.getAveragePrice(), expected.getAveragePrice())
                || !decimalEquals(actual.getCumulativeFee(), expected.getCumulativeFee())
                || !Objects.equals(actual.getFeeAsset(), expected.getFeeAsset())
                || !fillsEqual(actual.getFills(), expected.getFills())
                || !Objects.equals(actual.getAcceptedAt(), expected.getAcceptedAt())
                || !Objects.equals(actual.getSubmittedAt(), expected.getSubmittedAt())
                || !Objects.equals(actual.getLastUpdatedAt(), expected.getLastUpdatedAt())
                || !Objects.equals(actual.getCompletedAt(), expected.getCompletedAt())
                || !Objects.equals(actual.getTerminalErrorCode(), expected.getTerminalErrorCode())
                || !Objects.equals(actual.getTerminalErrorMessage(), expected.getTerminalErrorMessage())) {
            throw new ExecutionOrderException(EXECUTION_ORDER_RESTORE_MISMATCH,
                    "replayed snapshot does not match persisted fields");
        }
    }

    private boolean fillsEqual(List<ExecutionFill> actual, List<ExecutionFill> expected) {
        if (actual.size() != expected.size()) return false;
        for (int index = 0; index < actual.size(); index++) {
            ExecutionFill left = actual.get(index);
            ExecutionFill right = expected.get(index);
            if (!Objects.equals(left.getFillId(), right.getFillId())
                    || !decimalEquals(left.getQuantity(), right.getQuantity())
                    || !decimalEquals(left.getPrice(), right.getPrice())
                    || !decimalEquals(left.getFee(), right.getFee())
                    || !Objects.equals(left.getFeeAsset(), right.getFeeAsset())
                    || !Objects.equals(left.getFilledAt(), right.getFilledAt())) return false;
        }
        return true;
    }

    private boolean decimalEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private ExecutionOrderException restoreFailure(String message) {
        return error(EXECUTION_ORDER_RESTORE_INVALID, message);
    }
    public ExecutionOrderSnapshot create(ExecutionOrderRequest request) {
        if (request == null) throw error("EXECUTION_ORDER_REQUEST_INVALID", "request is null");
        return ExecutionOrderSnapshot.created(request);
    }

    public ExecutionOrderSnapshot accept(ExecutionOrderSnapshot snapshot, Instant acceptedAt) {
        require(snapshot, ExecutionOrderStatus.CREATED);
        requireTime(snapshot, acceptedAt, snapshot.getRequest().getRequestedAt());
        return transition(snapshot, ExecutionOrderStatus.ACCEPTED, null, acceptedAt, null,
                acceptedAt, null, null, null);
    }

    public ExecutionOrderSnapshot submit(ExecutionOrderSnapshot snapshot, String executionOrderId, Instant submittedAt) {
        require(snapshot, ExecutionOrderStatus.ACCEPTED);
        if (blank(executionOrderId)) throw error("EXECUTION_ORDER_ID_REQUIRED", "executionOrderId is blank");
        requireTime(snapshot, submittedAt, snapshot.getAcceptedAt());
        return transition(snapshot, ExecutionOrderStatus.SUBMITTED, executionOrderId, submittedAt, null,
                snapshot.getAcceptedAt(), submittedAt, null, null);
    }

    public ExecutionOrderSnapshot applyFill(ExecutionOrderSnapshot snapshot, ExecutionFill fill) {
        if (snapshot == null || fill == null) {
            if (fill == null) throw error("EXECUTION_ORDER_FILL_INVALID", "fill is null");
            require(snapshot, ExecutionOrderStatus.SUBMITTED, ExecutionOrderStatus.PARTIALLY_FILLED);
        }
        require(snapshot, ExecutionOrderStatus.SUBMITTED, ExecutionOrderStatus.PARTIALLY_FILLED);
        if (snapshot.getFills().stream().anyMatch(existing -> existing.getFillId().equals(fill.getFillId()))) {
            throw error("EXECUTION_ORDER_DUPLICATE_FILL", "fillId=" + fill.getFillId());
        }
        Instant previousEvent = snapshot.getFills().isEmpty() ? snapshot.getSubmittedAt()
                : snapshot.getFills().get(snapshot.getFills().size() - 1).getFilledAt();
        requireTime(snapshot, fill.getFilledAt(), previousEvent);
        BigDecimal filled = snapshot.getFilledQuantity().add(fill.getQuantity());
        int quantityComparison = filled.compareTo(snapshot.getRequest().getQuantity());
        if (quantityComparison > 0) throw error("EXECUTION_ORDER_OVERFILLED", "filledQuantity=" + filled);
        BigDecimal remaining = snapshot.getRequest().getQuantity().subtract(filled);
        BigDecimal average = weightedAverage(snapshot, fill, filled);
        BigDecimal fee = snapshot.getCumulativeFee().add(fill.getFee());
        if (snapshot.getFeeAsset() != null && !snapshot.getFeeAsset().equals(fill.getFeeAsset())) {
            throw error("EXECUTION_ORDER_FEE_ASSET_CONFLICT", "feeAsset=" + fill.getFeeAsset());
        }
        List<ExecutionFill> fills = new ArrayList<>(snapshot.getFills());
        fills.add(fill);
        ExecutionOrderStatus status = quantityComparison == 0 ? ExecutionOrderStatus.FILLED : ExecutionOrderStatus.PARTIALLY_FILLED;
        Instant completedAt = quantityComparison == 0 ? fill.getFilledAt() : snapshot.getCompletedAt();
        return ExecutionOrderSnapshot.next(snapshot, status, snapshot.getExecutionOrderId(), filled, remaining, average, fee,
                fill.getFeeAsset(), fills, snapshot.getAcceptedAt(), snapshot.getSubmittedAt(), fill.getFilledAt(), completedAt,
                null, null);
    }

    public ExecutionOrderSnapshot cancel(ExecutionOrderSnapshot snapshot, Instant canceledAt) {
        require(snapshot, ExecutionOrderStatus.SUBMITTED, ExecutionOrderStatus.PARTIALLY_FILLED);
        requireTime(snapshot, canceledAt, lastEvent(snapshot));
        return transition(snapshot, ExecutionOrderStatus.CANCELED, snapshot.getExecutionOrderId(), canceledAt, canceledAt,
                snapshot.getAcceptedAt(), snapshot.getSubmittedAt(), null, null);
    }

    public ExecutionOrderSnapshot reject(ExecutionOrderSnapshot snapshot, String errorCode, String errorMessage, Instant rejectedAt) {
        require(snapshot, ExecutionOrderStatus.CREATED, ExecutionOrderStatus.ACCEPTED);
        requireError(errorCode, errorMessage);
        requireTime(snapshot, rejectedAt, lastEvent(snapshot));
        return transition(snapshot, ExecutionOrderStatus.REJECTED, snapshot.getExecutionOrderId(), rejectedAt, rejectedAt,
                snapshot.getAcceptedAt(), snapshot.getSubmittedAt(), errorCode, errorMessage);
    }

    public ExecutionOrderSnapshot fail(ExecutionOrderSnapshot snapshot, String errorCode, String errorMessage, Instant failedAt) {
        require(snapshot, ExecutionOrderStatus.ACCEPTED, ExecutionOrderStatus.SUBMITTED, ExecutionOrderStatus.PARTIALLY_FILLED);
        requireError(errorCode, errorMessage);
        requireTime(snapshot, failedAt, lastEvent(snapshot));
        return transition(snapshot, ExecutionOrderStatus.FAILED, snapshot.getExecutionOrderId(), failedAt, failedAt,
                snapshot.getAcceptedAt(), snapshot.getSubmittedAt(), errorCode, errorMessage);
    }

    private ExecutionOrderSnapshot transition(ExecutionOrderSnapshot old, ExecutionOrderStatus status, String executionOrderId,
                                               Instant eventAt, Instant completedAt, Instant acceptedAt, Instant submittedAt,
                                               String errorCode, String errorMessage) {
        return ExecutionOrderSnapshot.next(old, status, executionOrderId, old.getFilledQuantity(), old.getRemainingQuantity(),
                old.getAveragePrice(), old.getCumulativeFee(), old.getFeeAsset(), old.getFills(), acceptedAt,
                submittedAt, eventAt, completedAt, errorCode, errorMessage);
    }

    private BigDecimal weightedAverage(ExecutionOrderSnapshot snapshot, ExecutionFill fill, BigDecimal totalQuantity) {
        BigDecimal notional = fill.getQuantity().multiply(fill.getPrice());
        if (snapshot.getFilledQuantity().signum() > 0) {
            notional = notional.add(snapshot.getAveragePrice().multiply(snapshot.getFilledQuantity()));
        }
        return notional.divide(totalQuantity);
    }

    private Instant lastEvent(ExecutionOrderSnapshot snapshot) {
        if (!snapshot.getFills().isEmpty()) return snapshot.getFills().get(snapshot.getFills().size() - 1).getFilledAt();
        if (snapshot.getSubmittedAt() != null) return snapshot.getSubmittedAt();
        if (snapshot.getAcceptedAt() != null) return snapshot.getAcceptedAt();
        return snapshot.getRequest().getRequestedAt();
    }

    private void require(ExecutionOrderSnapshot snapshot, ExecutionOrderStatus... allowed) {
        if (snapshot == null) throw error("EXECUTION_ORDER_TRANSITION_INVALID", "snapshot is null");
        for (ExecutionOrderStatus status : allowed) if (snapshot.getStatus() == status) return;
        throw error("EXECUTION_ORDER_TRANSITION_INVALID", "status=" + snapshot.getStatus());
    }

    private void requireTime(ExecutionOrderSnapshot snapshot, Instant actual, Instant previous) {
        if (actual == null || previous == null || actual.isBefore(previous)) {
            throw error("EXECUTION_ORDER_TIME_INVALID", "event time moved backwards");
        }
    }

    private void requireError(String code, String message) {
        if (blank(code) || blank(message)) throw error("EXECUTION_ORDER_REQUEST_INVALID", "error code and message are required");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ExecutionOrderException error(String code, String message) { return new ExecutionOrderException(code, message); }
}
