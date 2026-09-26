package io.github.dochiri0916.riskgate.global.exception;

import java.util.Map;

public abstract class DomainException extends BusinessException {
    protected DomainException(final ErrorCode errorCode, final Map<String, Object> extensions) {
        super(errorCode, extensions);
    }
}
