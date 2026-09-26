# RiskGate 구현 계획

## 목표와 현재 구현

RiskGate는 정적 검증 결과, 변경 정보, 분석기 결과를 종합해 운영 위험을 평가하고 `PASS`, `REVIEW`, `BLOCK`을 반환한다. AI는 분석 근거와 점수를 제공하고, 최종 판정은 버전이 추적되는 결정론적 정책이 내린다.

현재 risk-gate 저장소에는 버전이 지정된 리포트 입력, 위험 분석 Port와 결정론적 임시 Analyzer, 정책 판정, HTTP API, 공통 ProblemDetail 오류 응답이 구현되어 있다. build-convention의 리포트 생산자와 CI 게시 연결, 감사 DB, 실제 AI 연결, Project Registry는 후속 작업이다.

## MVP 계약과 정책

### 평가 API

`POST /api/v1/risk-assessments`는 JSON 리포트를 받고 판정을 반환한다. 요청은 다음 필드를 포함한다.

```json
{
  "reportVersion": "1",
  "repository": "org/service",
  "commitSha": "abcdef1234567",
  "pullRequestNumber": 123,
  "checks": [
    { "tool": "BUILD_CONVENTION", "name": "check", "status": "PASS" }
  ],
  "findings": [
    {
      "category": "CONCURRENCY",
      "severity": "HIGH",
      "source": "AI",
      "summary": "동시 갱신 위험",
      "evidence": "재고 차감 구간"
    }
  ],
  "changedFiles": [
    { "path": "src/main/java/InventoryService.java", "changeType": "MODIFIED" }
  ]
}
```

점수와 판정, 이유 코드, 정책 버전은 응답으로 반환한다. 지원하는 리포트 버전은 `1`이다. 요청 검증 실패는 400, 지원하지 않는 버전은 안정된 ErrorCode를 포함한 RFC 9457 ProblemDetail로 응답한다.

### 판정 정책

- `BUILD_CONVENTION`의 `FAIL`은 즉시 `BLOCK`한다.
- `SEMGREP` 출처의 `CRITICAL` finding은 즉시 `BLOCK`한다.
- 나머지는 가장 높은 finding severity를 임시 점수로 사용한다: `LOW=15`, `MEDIUM=40`, `HIGH=70`, `CRITICAL=90`.
- 점수 90 이상은 `BLOCK`, 70 이상은 `REVIEW`, 그 미만은 `PASS`다.
- 응답에 `BUILD_CONVENTION_FAILED`, `SEMGREP_CRITICAL`, 임계값 판정 이유 코드를 남긴다.

임시 Analyzer는 입력 finding을 점수화하는 자리표시자다. 변경 diff의 의미 분석은 실제 RiskAnalyzer Port 어댑터를 연결한 뒤 제공한다. 빈 findings는 점수 0으로 판정하며, 검사 목록은 최소 한 항목이어야 한다.

## 구현 단계

1. **리포트 생산자 연동**: build-convention에서 저장소·커밋·PR 식별 정보, 검사별 결과, finding, 변경 파일을 스키마 버전 `1` JSON으로 출력한다. JSON Schema와 예제 fixture를 제공하고 RiskGate 요청 계약과 계약 테스트를 공유한다. 현재 build-convention 저장소에는 이 생산 기능이 없어 RiskGate API를 CI에 연결하기 전에 선행해야 한다. 해당 저장소는 현재 작업 경로의 쓰기 허용 범위 밖이다.
2. **CI end-to-end**: CI에서 `./gradlew check`, Semgrep, 리포트 생성을 순서대로 실행하고 RiskGate API를 호출한다. 응답 판정을 필수 상태 검사로 게시한다. RiskGate 호출 실패, timeout, 스키마 오류를 성공으로 처리하지 않는다.
3. **실제 위험 분석기**: Dummy Adapter 대신 Jev 기반 어댑터를 연결한다. Analyzer Port는 리포트와 diff 맥락을 받고 카테고리, severity, 요약, 안전한 근거를 반환한다. AI 출력은 점수 산정 자료로만 사용하고 최종 결정을 직접 내리지 않는다.
4. **감사 이력**: Audit 저장소 Port와 Persistence Adapter를 추가한다. 입력 식별자, 분석기·정책 버전, 점수, finding, 판정, 수행 시각을 기록하고 조회 API를 제공한다. 도메인 Aggregate와 JPA Entity는 분리한다.
5. **프로젝트 맥락과 운영 입력**: Project Registry/Service Catalog에서 서비스·도메인·인프라 메타데이터를 조회한다. 이후 성능 측정 결과, SonarQube 등 추가 입력을 별도 어댑터로 연결한다.
6. **평가 회귀와 관측**: 과거 장애 사례 Golden Eval Set으로 Analyzer·정책 변경을 평가한다. Actuator/Micrometer 지표에 요청 수, 판정별 수, 분석 지연과 실패 수를 추가하고 Prometheus에서 수집한다.
7. **업무 입력 확장**: 기본 PR/CI 평가 흐름이 안정화된 뒤 GitHub·Slack 이벤트, DevForge와 DB/Kafka 성능 실험 결과를 연결한다.

## 구조와 품질 기준

- 기반 패키지는 `global`과 `riskassessment` bounded context로 나눈다. `domain <- application <- adapter` 방향을 지킨다.
- Controller는 Inbound UseCase만 호출한다. Application Service는 하나의 UseCase와 자신이 소유한 Port만 사용한다.
- Domain은 순수 Java 타입과 불변 모델을 사용한다. Web DTO와 Application Command/Result, Persistence Entity를 분리한다.
- Context·계층별 오류 계약과 공통 ProblemDetail 처리 기준은 `build-convention/docs/ERROR_HANDLING.md`를 따른다.
- 테스트는 한국어 `@DisplayName`, `// given`, `// when`, `// then`을 사용한다. 정상 판정, hard gate, 점수 경계(69/70/89/90), critical finding, 잘못된 입력, 미지원 버전, Analyzer 실패를 검증한다.
- `./gradlew check`를 필수 검증으로 사용하고 실패를 감추기 위해 컨벤션이나 테스트를 비활성화하지 않는다.

## 가정과 남은 작업

- 초기 임계값은 도구 요약 문서의 예시값인 70/90을 따른다. 운영 데이터와 Golden Eval 결과에 따라 정책 버전을 올려 변경한다.
- RiskGate MVP는 외부 저장소 없이 동작한다. 감사 이력은 별도 단계에서 추가한다.
- 현재 요청/응답은 RiskGate 내부 계약 초안이다. build-convention 생산자 구현 전 JSON Schema와 필드 호환성을 양쪽 저장소에서 확정해야 한다.
- RiskGate `./gradlew check`는 테스트·정적 분석·아키텍처·도메인 커버리지 검증을 통과한다. 변경 코드 커버리지 태스크는 현재 작업 디렉터리에 Git 메타데이터와 비교 ref가 없어 실행할 수 없다.
