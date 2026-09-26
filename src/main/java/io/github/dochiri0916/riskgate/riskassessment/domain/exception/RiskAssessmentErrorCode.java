package io.github.dochiri0916.riskgate.riskassessment.domain.exception;

import io.github.dochiri0916.riskgate.global.exception.ErrorCode;
import io.github.dochiri0916.riskgate.global.exception.ErrorKind;

public enum RiskAssessmentErrorCode implements ErrorCode {
    INVALID_REPORT_SCHEMA("RISK-ASSESSMENT-001", "지원하지 않는 리포트 스키마 버전입니다."),
    INVALID_SCORE("RISK-ASSESSMENT-002", "위험 점수는 0부터 100 사이여야 합니다."),
    INVALID_POLICY_CONFIGURATION("RISK-ASSESSMENT-003", "위험 정책 임계값 구성이 올바르지 않습니다."),
    INVALID_POLICY_INPUT("RISK-ASSESSMENT-004", "위험 정책 입력이 올바르지 않습니다."),
    INVALID_BUILD_CONVENTION_SCHEMA("RISK-ASSESSMENT-005", "지원하지 않는 Build Convention 리포트 스키마 버전입니다."),
    DIFF_TOO_LARGE("RISK-ASSESSMENT-006", "Git diff 크기 제한을 초과했습니다.");

    private final String valueCode;
    private final String detailMessage;

    RiskAssessmentErrorCode(final String code, final String detail) {
        this.valueCode = code;
        this.detailMessage = detail;
    }

    @Override
    public String code() {
        return valueCode;
    }

    @Override
    public ErrorKind kind() {
        return ErrorKind.INVALID_INPUT;
    }

    @Override
    public String detail() {
        return detailMessage;
    }
}
