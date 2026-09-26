package io.github.dochiri0916.riskgate.global.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTests {
    @Test
    @DisplayName("공통 오류 종류를 RFC 9457 status와 안정된 code로 변환한다")
    void mapErrorKindsToProblemDetails() {
        // given
        final List<TestErrorCode> errorCodes = List.of(
                new TestErrorCode("TEST-400", ErrorKind.INVALID_INPUT, "잘못된 입력"),
                new TestErrorCode("TEST-404", ErrorKind.NOT_FOUND, "대상을 찾을 수 없음"),
                new TestErrorCode("TEST-409", ErrorKind.CONFLICT, "충돌"),
                new TestErrorCode("TEST-403", ErrorKind.FORBIDDEN, "권한 없음"),
                new TestErrorCode("TEST-409-STATE", ErrorKind.INVALID_STATE, "잘못된 상태"),
                new TestErrorCode("TEST-500", ErrorKind.INTERNAL_ERROR, "내부 오류")
        );
        final List<Integer> expectedStatuses = List.of(400, 404, 409, 403, 409, 500);
        final GlobalExceptionHandler handler = new GlobalExceptionHandler();

        // when
        final List<ProblemDetail> problemDetails = errorCodes.stream()
                .map(errorCode -> handler.handleBusinessException(new FixtureBusinessException(errorCode)))
                .toList();

        // then
        assertEquals(
                expectedStatuses,
                problemDetails.stream().map(ProblemDetail::getStatus).toList(),
                "오류 종류를 HTTP status로 변환한다"
        );
        assertEquals("TEST-400", problemDetails.get(0).getProperties().get("code"), "ErrorCode를 응답 properties에 보존한다");
        assertEquals("잘못된 입력", problemDetails.get(0).getDetail(), "ErrorCode 안전 문구를 응답 detail로 사용한다");
    }

    private record TestErrorCode(String code, ErrorKind kind, String detail) implements ErrorCode { }

    private static final class FixtureBusinessException extends BusinessException {
        private static final long serialVersionUID = 1L;

        private FixtureBusinessException(final ErrorCode errorCode) {
            super(errorCode, Map.of());
        }
    }
}
