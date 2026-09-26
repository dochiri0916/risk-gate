package io.github.dochiri0916.riskgate.global.exception;

import java.util.Map;

public abstract class ApplicationException extends BusinessException {
    protected ApplicationException(final ErrorCode errorCode, final Map<String, Object> extensions) {
        super(errorCode, extensions);
    }
}
