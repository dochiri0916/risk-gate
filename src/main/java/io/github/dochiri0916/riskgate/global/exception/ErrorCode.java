package io.github.dochiri0916.riskgate.global.exception;

public interface ErrorCode {
    String code();

    ErrorKind kind();

    String detail();
}
