package io.github.dochiri0916.riskgate.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public final class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(final BusinessException exception) {
        final HttpStatus status = statusOf(exception.errorCode().kind());
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                status,
                exception.errorCode().detail()
        );
        problemDetail.setTitle(exception.errorCode().code());
        problemDetail.setProperty("code", exception.errorCode().code());
        exception.extensions().forEach(problemDetail::setProperty);
        return problemDetail;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            final MethodArgumentNotValidException exception,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request
    ) {
        final ErrorCode errorCode = GlobalErrorCode.INVALID_REQUEST;
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                statusOf(errorCode.kind()),
                errorCode.detail()
        );
        problemDetail.setTitle(errorCode.code());
        problemDetail.setProperty("code", errorCode.code());
        return createResponseEntity(problemDetail, headers, statusOf(errorCode.kind()), request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(final Exception exception) {
        logger.error("Unexpected request failure", exception);
        final ErrorCode errorCode = GlobalErrorCode.INTERNAL_ERROR;
        final HttpStatus status = statusOf(errorCode.kind());
        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                status,
                errorCode.detail()
        );
        problemDetail.setTitle(errorCode.code());
        problemDetail.setProperty("code", errorCode.code());
        return problemDetail;
    }

    private HttpStatus statusOf(final ErrorKind kind) {
        return switch (kind) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT, INVALID_STATE -> HttpStatus.CONFLICT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
