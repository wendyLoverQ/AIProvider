package com.aiprovider.quant.supervisor.paper;

import java.util.Objects;

public final class PaperSessionSupervisorFailure {
    private final String errorCode;
    private final String message;
    private final Throwable cause;

    public PaperSessionSupervisorFailure(String errorCode, String message, Throwable cause) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        this.errorCode = errorCode;
        this.message = message;
        this.cause = cause;
    }

    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public Throwable getCause() { return cause; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperSessionSupervisorFailure that)) return false;
        return errorCode.equals(that.errorCode) && message.equals(that.message)
                && Objects.equals(cause, that.cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorCode, message, cause);
    }
}
