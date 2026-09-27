# Risk Gate Repository Guidance

이 저장소는 코드 변경의 위험 신호를 분석하고 최종적으로 `PASS`, `REVIEW`, `BLOCK`을 결정하는 Policy Gate다.

이 문서는 **Risk Gate 자체를 개발하거나 수정할 때** 적용한다.

일반 Java/Spring 구현 규칙은 전역 Build Convention guidance를 따른다.

## Core Responsibility

역할을 다음과 같이 유지한다.

```text
Input Adapter
→ Risk Analysis
→ RiskPolicyService
→ PASS / REVIEW / BLOCK
```

최종 `RiskDecision`의 소유자는 `RiskPolicyService`다.

다른 계층에서 최종 정책을 중복 구현하지 않는다.

## Analyzer

Analyzer는 다음 역할만 담당한다.

- RiskFinding 생성 또는 수집
- finding을 기반으로 RiskScore 계산

Analyzer가 직접 최종 `PASS`, `REVIEW`, `BLOCK`을 결정하지 않는다.

새 analyzer를 추가해도 기존 `RiskAnalyzerPort` 경계를 우선 사용한다.

## Policy

기존 policy threshold와 severity-to-score mapping을 테스트 편의를 위해 변경하지 않는다.

다음과 같은 deterministic signal은 기존 정책에 따라 처리한다.

- Build Convention FAIL
- CI Semgrep CRITICAL
- Local deterministic risk finding

새 signal을 추가할 때 adapter 내부에서 직접 BLOCK시키지 말고 기존 policy 흐름에 연결한다.

## Build Convention

Risk Gate는 Build Convention의 규칙을 다시 구현하지 않는다.

Build Convention report를 입력으로 소비한다.

Build Convention이 FAIL이면 기존 deterministic BLOCK 정책을 유지한다.

Build Convention의 개별 architecture/test/coverage rule을 Risk Gate에서 중복 검사하지 않는다.

## Semgrep

CI Mode에서는 기존 Semgrep 결과를 소비한다.

Semgrep `CRITICAL` finding의 deterministic BLOCK 정책을 유지한다.

Local Mode에서는 Semgrep을 실행하지 않는다.

실행하지 않은 Semgrep 결과를 다음처럼 표현하지 않는다.

```text
PASS
```

반드시:

```text
NOT_RUN
```

으로 구분한다.

## Local Mode

Local Mode는 현재 Git working tree를 분석한다.

다음을 포함한다.

- committed branch changes
- staged changes
- unstaged changes
- untracked files
- renamed files
- deleted files

Local Mode 때문에 외부 HTTP 서버 실행을 요구하지 않는다.

Local Mode 때문에 외부 AI Provider나 외부 network dependency를 추가하지 않는다.

Local deterministic analysis는 실제 diff와 changedFiles에서 finding을 생성하되 최종 RiskDecision은 만들지 않는다.

## Local Risk Signals

Local signal extractor는 deterministic finding만 생성한다.

예:

- secret material 추가
- destructive migration
- security-sensitive 변경
- CI/CD 변경
- build configuration 변경
- deployment configuration 변경

일반적인 application code 변경이라는 이유만으로 임의의 HIGH 또는 CRITICAL finding을 만들지 않는다.

가능하면 added diff line을 기준으로 신규 위험을 판단한다.

## Security

다음을 로그, JSON 응답, exception message에 노출하지 않는다.

- raw diff 전체
- secret 값
- token
- password
- credential
- private key material

finding에는 필요한 최소 정보만 제공한다.

예:

```text
file path
rule code
severity
```

실제 secret 값은 포함하지 않는다.

## API Compatibility

Local Mode 또는 analyzer 변경으로 기존 기능을 깨뜨리지 않는다.

특히 다음 contract를 유지한다.

- `POST /api/v1/risk-assessments`
- reusable GitHub Actions workflow
- Build Convention deterministic BLOCK
- Semgrep CRITICAL deterministic BLOCK
- 기존 response contract

## CLI

Local CLI exit code 의미를 유지한다.

```text
0 = PASS
2 = REVIEW
3 = BLOCK
4 = 입력 또는 실행 오류
```

`BLOCK`과 내부 오류를 같은 상태로 처리하지 않는다.

`REVIEW`를 `PASS`로 취급하지 않는다.

## Tests

Risk rule 또는 signal을 추가할 때 가능한 경우 다음을 모두 검증한다.

- PASS
- REVIEW
- BLOCK
- ERROR

Local Git 분석은 temporary repository를 이용해 다음 상태를 실제로 검증한다.

- staged
- unstaged
- untracked
- rename
- delete

production threshold를 테스트에 맞추기 위해 변경하지 않는다.

## Completion

완료 전에 실행한다.

```bash
./gradlew clean check
```

Local Mode 변경이라면 실제 Local CLI도 실행한다.

기존 API와 GitHub workflow 관련 테스트도 유지한다.