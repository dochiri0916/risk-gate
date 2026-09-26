package io.github.dochiri0916.riskgate.global.exception;

import java.util.Map;
import java.util.Objects;

public abstract class BusinessException extends RuntimeException {
    private final ErrorCode storedErrorCode;
    private final Map<String, Object> storedExtensions;

    protected BusinessException(final ErrorCode errorCode, final Map<String, Object> extensions) {
        super(Objects.requireNonNull(errorCode).detail());
        this.storedErrorCode = errorCode;
        this.storedExtensions = Map.copyOf(extensions);
    }

    public ErrorCode errorCode() {
        return storedErrorCode;
    }

    public Map<String, Object> extensions() {
        return storedExtensions;
    }
}
