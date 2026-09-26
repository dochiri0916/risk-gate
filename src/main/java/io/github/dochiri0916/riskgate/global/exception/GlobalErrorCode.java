package io.github.dochiri0916.riskgate.global.exception;

public enum GlobalErrorCode implements ErrorCode {
    INVALID_REQUEST("RISKGATE-400", "요청 값이 올바르지 않습니다."),
    INTERNAL_ERROR("RISKGATE-500", "요청을 처리하는 중 오류가 발생했습니다.");

    private final String valueCode;
    private final String detailMessage;

    GlobalErrorCode(final String valueCode, final String detailMessage) {
        this.valueCode = valueCode;
        this.detailMessage = detailMessage;
    }

    @Override
    public String code() {
        return valueCode;
    }

    @Override
    public ErrorKind kind() {
        return this == INVALID_REQUEST ? ErrorKind.INVALID_INPUT : ErrorKind.INTERNAL_ERROR;
    }

    @Override
    public String detail() {
        return detailMessage;
    }
}
