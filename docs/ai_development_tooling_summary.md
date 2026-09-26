# AI 개발 자동화 / 검증 도구 정리

## 1. 전체 구조

지금까지 논의한 구조는 크게 세 계층으로 나뉜다.

```text
DevForge
= AI가 실제 업무를 수행하는 계층

        ↓ 코드 / PR / 분석 결과

build-convention
= 결정론적으로 검증 가능한 규칙을 강제하는 계층

        ↓ 검사 결과

RiskGate
= 변경의 의미적·운영적 위험을 판단하는 계층

        ↓

PASS / REVIEW / BLOCK
```

핵심 원칙은 다음과 같다.

- **AI가 판단할 필요가 없는 것은 코드와 도구로 검증한다.**
- **AI는 의미·맥락·운영 위험처럼 결정론적으로 판단하기 어려운 영역에만 사용한다.**
- **AI가 만든 결과를 AI 하나만으로 승인하지 않는다.**
- **생성 자동화보다 검증 자동화를 우선한다.**
- **모델과 추론 강도는 작업 난이도에 따라 단계적으로 올린다.**

---

# 2. build-convention

## 역할

Java/Spring 프로젝트에서 자동으로 검증 가능한 개발·아키텍처 규칙을 `./gradlew check`에 통합하는 Gradle Convention Plugin.

현재 프로젝트에는 다음 기능이 이미 포함되어 있다.

| 도구 / 기능 | 역할 | 현재 상태 |
|---|---|---|
| Checkstyle | 코드 스타일 / 포맷 규칙 검사 | 적용 |
| PMD | 소스 코드 품질 / 안티패턴 검사 | 적용 |
| SpotBugs | Java bytecode 기반 버그 패턴 탐지 | 적용 |
| ArchUnit | 아키텍처 의존성 규칙 검증 | 적용 |
| Custom Validator | 패키지, 네이밍, 계층, Aggregate 등 사내 규칙 검증 | 적용 |
| JaCoCo | Line / Branch Coverage 측정 | 적용 |
| Changed Code Coverage | 변경된 코드의 커버리지 검증 | 적용 |
| PIT | Mutation Testing | 적용 가능 |
| Migration Validator | DB migration 규칙 검증 | 적용 |
| Git Hook / CI | `./gradlew check` 우회 방지 | 적용 가능 |

## build-convention이 담당해야 하는 것

```text
"이 코드는 우리가 정의한 규칙을 위반했는가?"
```

예:

- Controller → Repository 직접 접근
- Application → Adapter 의존
- 다른 Bounded Context의 Domain 직접 참조
- JPA Entity / Domain 모델 규칙 위반
- 테스트에 assertion 없음
- Coverage 기준 미달
- Migration index / FK 규칙 위반
- PIT mutation score 기준 미달

## 담당하지 않는 것

다음은 RiskGate가 담당한다.

- 동시성 위험
- 멱등성 누락 가능성
- 트랜잭션 경계의 운영 위험
- 성능 영향
- 배포 위험
- 비즈니스 의미상의 잘못된 설계
- 실제 서비스 맥락 기반 위험도

---

# 3. Checkstyle

## 목적

정해진 Java 코드 스타일을 강제한다.

대표 검사:

- 들여쓰기
- import 순서
- naming
- line length
- 금지된 문법 스타일

## 적합한 역할

```text
"코드 스타일이 조직 규칙과 일치하는가?"
```

---

# 4. PMD

## 목적

소스 코드를 분석하여 코드 품질 문제와 안 좋은 패턴을 탐지한다.

대표 검사:

- 불필요한 코드
- 복잡한 메서드
- 사용하지 않는 변수
- 빈 catch
- 좋지 않은 객체 생성 패턴
- 지나치게 복잡한 조건문

## 적합한 역할

```text
"소스 코드 구조에 품질상 문제가 있는가?"
```

---

# 5. SpotBugs

## 목적

컴파일된 Java bytecode를 분석하여 실제 버그로 이어질 가능성이 높은 패턴을 탐지한다.

대표 검사:

- NullPointerException 가능성
- equals/hashCode 오류
- 리소스 누수
- 동기화 문제
- 잘못된 API 사용

## PMD와 차이

```text
PMD
→ 소스 코드 품질 / 규칙

SpotBugs
→ 실제 Java 버그 패턴
```

---

# 6. ArchUnit

## 목적

코드의 아키텍처 규칙을 테스트 코드로 강제한다.

예:

```text
Controller → Repository 직접 접근 금지
Domain → Application/Adapter 의존 금지
Application → Adapter 의존 금지
```

## 적합한 역할

```text
"코드 구조가 우리가 정한 아키텍처 경계를 지키는가?"
```

AI에게 맡길 필요가 없는 대표적인 결정론적 검사다.

---

# 7. JaCoCo

## 목적

테스트 커버리지를 측정한다.

주요 지표:

- Line Coverage
- Branch Coverage

## 주의점

Coverage가 높다고 테스트 품질이 반드시 높은 것은 아니다.

```text
Coverage 100%
≠
버그 탐지 능력 100%
```

그래서 PIT Mutation Testing과 같이 사용하는 것이 좋다.

---

# 8. PIT / Mutation Testing

## 개념

구현 코드를 테스트 도구가 일부러 망가뜨린 후 기존 테스트가 그 오류를 잡는지 확인한다.

예:

```java
if (stock < quantity)
```

를 다음과 같이 변형:

```java
if (stock <= quantity)
```

그리고 테스트를 다시 실행한다.

### Mutation Killed

테스트 실패:

```text
테스트가 변경된 버그를 발견함
→ 좋은 테스트
```

### Mutation Survived

테스트 통과:

```text
구현을 잘못 바꿨는데도 테스트가 통과함
→ 테스트 부족 가능성
```

## 주요 지표

```text
Mutation Score
```

## 역할

```text
"작성한 테스트가 실제 코드 오류를 잡을 수 있는가?"
```

현재 build-convention에는 PIT gate 연동 구조가 이미 존재한다.

---

# 9. Property-Based Testing

## 추천 Java 도구

```text
jqwik
```

## 개념

특정 입력값 하나를 테스트하는 대신 항상 만족해야 하는 **Property / 불변식**을 정의한다.

일반 테스트:

```text
재고 10개에서 3개 차감 → 7개
```

Property Test:

```text
어떤 유효한 입력에서도 재고는 음수가 되면 안 된다.
```

테스트 프레임워크가 다양한 입력을 자동 생성해서 검증한다.

## 잘 맞는 대상

- 금액은 음수가 될 수 없음
- 재고는 음수가 될 수 없음
- CANCELLED 상태는 다시 변경될 수 없음
- 같은 idempotency key는 한 번만 처리
- 특정 상태 전이만 허용

## 역할

```text
"우리가 생각하지 못한 입력값에서도 도메인 불변식이 유지되는가?"
```

## Mutation Test와 차이

| 구분 | Property Test | Mutation Test |
|---|---|---|
| 변경 대상 | 입력값 | 구현 코드 |
| 목적 | Edge Case 탐색 | 테스트 자체 품질 검증 |
| 대표 Java 도구 | jqwik | PIT |

---

# 10. Semgrep

## 목적

소스 코드 패턴을 기반으로 보안·조직 규칙을 검사하는 SAST 도구.

예:

- 금지된 API 사용
- 위험한 SQL 조립
- 보안 설정 제거
- Secret 하드코딩
- 특정 위험 메서드 사용

## 추천 위치

build-convention 내부보다 **CI 별도 단계**를 권장한다.

```text
CI

./gradlew check
+
semgrep scan
        ↓
RiskGate
```

이유:

- Java/Gradle 전용 도구가 아님
- 외부 CLI 의존성 발생
- 언어 독립적인 보안 검사로 확장 가능

---

# 11. SonarQube / SonarCloud

## 목적

프로젝트 전반의 코드 품질과 보안을 중앙에서 관리한다.

주요 기능:

- Bug
- Vulnerability
- Code Smell
- 중복 코드
- 복잡도
- Coverage
- Quality Gate

## 역할

```text
"프로젝트 전체 품질이 정해진 기준을 만족하는가?"
```

RiskGate를 대체하는 도구가 아니라 **RiskGate가 참고할 수 있는 입력 데이터**에 가깝다.

---

# 12. RiskGate

## 역할

build-convention, Semgrep, 테스트, Git diff, 프로젝트 정보를 종합하여 **운영 위험**을 판단한다.

```text
build-convention PASS
        +
Semgrep 결과
        +
Git Diff
        +
Project Metadata
        +
성능 테스트 결과
        ↓
RiskGate
        ↓
Jev / AI Risk Analyzer
        ↓
Policy Engine
        ↓
PASS / REVIEW / BLOCK
```

## 초기 Risk Category

```text
CONCURRENCY
IDEMPOTENCY
TRANSACTION
DATA_INTEGRITY
PERFORMANCE
SECURITY
BREAKING_CHANGE
OPERABILITY
```

## 중요한 원칙

AI가 최종 merge 여부를 직접 결정하지 않는다.

```text
AI
→ Risk Score

Policy Engine
→ 실제 결정
```

예:

```text
build-convention FAIL
→ 무조건 BLOCK

Semgrep Critical
→ BLOCK

AI Risk >= 90
→ BLOCK

AI Risk >= 70
→ REVIEW

나머지
→ PASS
```

---

# 13. Jev

## 역할

비교적 저렴한 판단 모델로 활용.

주요 용도:

### 1. 작업 라우팅

```text
Task
↓
Jev
↓
난이도 / 위험도 점수
↓
적절한 모델 선택
```

### 2. RiskGate

```text
Git Diff
+
정적 분석 결과
+
서비스 맥락
↓
Jev
↓
동시성 / 멱등성 / 성능 / 운영 위험도
```

## 직접 담당시키지 않을 것

- Merge 여부
- 결제 승인
- DB 정합성
- 권한 검사
- 반드시 결정론적으로 처리되어야 하는 정책

---

# 14. AI Model Router

## 목적

작업에 따라 모델과 reasoning effort를 자동으로 조절하여 비용을 줄인다.

예시:

```text
단순 탐색
→ 저비용 모델 + Low

일반 구현
→ 중간 모델 + Medium

복잡한 트랜잭션 / 동시성
→ 고성능 모델 + High

실패 반복
→ 모델 또는 reasoning 단계 상승
```

## 추천 구조

```text
Task
 ↓
Jev Router
 ↓
Luna Low
 ↓ 실패
Terra Medium
 ↓ 실패
Sol Medium
 ↓ 실패
Sol High
 ↓
최후의 고성능 모델
```

핵심:

```text
처음부터 최고 모델을 사용하지 않는다.
```

---

# 15. DevForge

## 역할

실제 AI 업무 수행 플랫폼.

```text
Slack / GitHub Issue / 사용자 명령
            ↓
      Project Resolver
            ↓
        Jev Router
            ↓
       Model Router
            ↓
      Coding / Analysis Agent
            ↓
       코드 수정 / 분석
            ↓
            PR
            ↓
        build-convention
            ↓
          RiskGate
```

## 주요 책임

- Task 관리
- Project Registry
- Service Catalog
- AI Model Routing
- Agent Execution
- Retry / Escalation
- 코드 생성
- 분석
- 성능 실험
- 결과 보고

---

# 16. Project Registry / Service Catalog

여러 프로젝트와 여러 Git repository를 AI가 정확히 찾기 위한 중앙 메타데이터.

예:

```yaml
projects:
  payment:
    repositories:
      - payment-api
      - payment-worker

    slack_channels:
      - payment-dev

    domains:
      - payment

    infrastructure:
      - mysql
      - kafka
```

## 추가 관리 대상

```text
Project
Repo
Service
Slack Channel
담당 팀
Domain
DB
Kafka Topic
API
Dependency
```

## 목적

```text
20개 Repo
↓
Project Resolver
↓
관련 Repo 2개
↓
관련 파일 8개
↓
AI
```

토큰 절약에도 매우 중요하다.

---

# 17. GitHub Actions / CI

## 역할

중앙 검증을 강제하는 실행 지점.

추천 흐름:

```text
PR
↓
./gradlew check
↓
Semgrep
↓
Git Diff 생성
↓
RiskGate
↓
PASS / REVIEW / BLOCK
```

Git Branch Ruleset에서 필수 Status Check로 등록한다.

## 로컬 Git Hook의 역할

```text
빠른 피드백
```

최종 강제는 CI가 담당한다.

---

# 18. Testcontainers

## 목적

통합 테스트에서 실제 DB/외부 컴포넌트와 최대한 유사한 환경을 생성한다.

예:

```text
MySQL Container
PostgreSQL Container
Kafka Container
```

H2 같은 대체 DB보다 실제 DB 동작을 검증하기 좋다.

---

# 19. Flyway

## 목적

DB Schema Migration 관리.

RiskGate 또는 build-convention에서 다음과 같은 변경을 별도 위험 항목으로 볼 수 있다.

- DROP
- TRUNCATE
- NOT NULL 추가
- 컬럼 타입 축소
- 대형 테이블 ALTER
- index 제거
- FK 제거

---

# 20. MySQL EXPLAIN ANALYZE

## 목적

실제 SQL 실행 계획과 수행 시간을 분석한다.

확인 가능:

- 실제 수행 시간
- 예상 row
- 실제 row
- loop
- scan 방식
- index 사용 여부

AI가 SQL 후보를 생성하고 실제 MySQL이 성능을 측정하는 구조가 좋다.

```text
AI
→ Query / Index 후보 생성

MySQL
→ EXPLAIN ANALYZE

Harness
→ 측정값 비교
```

---

# 21. MySQL Performance Schema

## 목적

실제 DB 실행 상태와 쿼리 성능 데이터를 수집한다.

활용 예:

- Query Digest
- 실행 횟수
- Lock Wait
- Connection
- Slow Query
- 쿼리별 비용

AI가 직접 성능을 추측하지 않고 실제 측정값을 기반으로 개선안을 제안하도록 한다.

---

# 22. k6

## 목적

부하 및 성능 테스트.

주요 테스트 종류:

### Smoke Test

기본 동작 확인.

### Load Test

예상 정상 트래픽 검증.

### Stress Test

정상 수준 이상의 부하에서 동작 확인.

### Spike Test

갑작스러운 트래픽 폭증 대응 확인.

### Breakpoint Test

시스템의 최대 처리 한계 탐색.

### Soak Test

수 시간 이상 장기간 부하를 유지하여 내구성 확인.

## 주요 지표

```text
TPS
p50
p95
p99
Error Rate
```

---

# 23. Soak / 내구도 테스트에서 볼 것

장시간 운영했을 때 다음 문제가 누적되는지 확인한다.

- Memory Leak
- Connection Leak
- Thread Leak
- GC 증가
- DB Pool 고갈
- Kafka Consumer Lag 증가
- Queue 적체
- Lock Wait 증가
- Deadlock
- Disk 증가
- Cache 비대화
- 시간이 지날수록 TPS 감소

---

# 24. Prometheus

## 목적

애플리케이션과 인프라 Metrics 수집.

수집 대상 예:

### Spring

```text
TPS
Latency
Error Rate
Heap
GC
Thread
HikariCP
```

### MySQL

```text
TPS/QPS
Connections
Lock Wait
Deadlock
CPU
Memory
Disk I/O
```

### Kafka

```text
Consumer Lag
Queue Depth
Retry
DLQ
Throughput
```

---

# 25. Grafana

## 목적

Prometheus 등의 Metrics를 시각화한다.

성능 실험 시 다음을 비교하기 좋다.

```text
Before
vs
Candidate A
vs
Candidate B
vs
Candidate C
```

---

# 26. Spring Boot Actuator / Micrometer

## 목적

Spring 애플리케이션의 운영 Metrics와 상태 정보를 노출한다.

RiskGate와 DevForge 모두 권장.

대표 데이터:

- Health
- JVM
- HTTP 요청
- DB Pool
- Custom Metric

Micrometer + Prometheus 조합으로 사용.

---

# 27. DB Performance Harness

AI가 여러 DB 구현 후보를 만들고 실제 측정 결과로 최적 후보를 찾는 자동화 환경.

```text
AI
↓
Query / Index 후보 생성
↓
동일 데이터셋
↓
Correctness Test
↓
EXPLAIN ANALYZE
↓
Load Test
↓
Stress / Spike
↓
Metrics 비교
↓
최적 후보 선택
```

## 반드시 바꿔가며 테스트할 것

### 데이터 크기

```text
1K
100K
10M
```

### 데이터 분포

```text
균등 분포
특정 상태 집중
특정 tenant 집중
최근 데이터 집중
NULL 비율 증가
```

### 동시 사용자

```text
1
10
100
500
```

## 성능 선택 기준 예

필수 조건:

```text
Correctness = PASS
Deadlock = 0
Error Rate < 기준
Write 성능 저하 < 기준
```

그 후:

```text
p95 최소
TPS 최대
```

등으로 선택.

---

# 28. AWS / Kafka Performance Lab

## 목표

실제 AWS 환경에서 Kafka 성능 테스트를 수행하되 비용을 최소화한다.

추천 구조:

```text
평소
AWS 테스트 환경 없음

테스트 시작
↓
Terraform apply
↓
Kafka / Load Generator / Monitoring 생성
↓
Load / Stress / Soak
↓
결과 저장
↓
Terraform destroy
```

## 핵심

상시 클러스터보다 **Ephemeral Performance Lab** 방식이 적합하다.

### 선택지

- EC2 + Kafka 직접 구성
- Amazon MSK Provisioned
- Amazon MSK Serverless

목적에 따라 선택.

---

# 29. Terraform

## 목적

성능 실험용 인프라를 코드로 생성하고 삭제한다.

```text
terraform apply
↓
테스트
↓
terraform destroy
```

AI가 밤새 실험하더라도 테스트가 끝난 뒤 인프라가 남아 비용이 계속 발생하지 않도록 한다.

---

# 30. Slack 업무 분석 Agent

## 목적

Slack에서 들어오는 멘션/업무 요청을 실시간으로 분석한다.

```text
Slack
↓
업무 분류
↓
Project Resolver
↓
관련 Thread / Repo / PR 확인
↓
분석
↓
사용자에게 보고
```

보고 예:

```text
긴급도
영향 범위
관련 서비스
최근 변경
가능한 원인
확인할 Metrics
권장 조사 순서
예상 수정 방향
```

바로 코드부터 수정하기보다 **분석 → 사람 확인 → 필요시 Coding Agent 실행** 구조가 안전하다.

---

# 31. Golden Eval Set

## 목적

AI 시스템 자체를 회귀 테스트한다.

과거 실제 장애/버그 사례를 보관한다.

예:

```text
중복 결제 장애
Lost Update
N+1
Transaction Boundary 문제
Kafka 멱등성 문제
```

RiskGate나 모델을 변경할 때:

```text
이전 사고들을 여전히 탐지하는가?
```

를 자동 평가한다.

---

# 32. Spec Traceability

도메인 명세와 테스트를 연결한다.

예:

```text
ORDER-INV-001
"취소된 주문은 다시 결제할 수 없다."

        ↓

OrderPropertyTest
#cancelledOrderCannotBePaid
```

CI에서:

```text
명세 규칙: 38
검증 테스트 연결: 37

ORDER-INV-017 미검증
→ FAIL
```

처럼 누락을 탐지할 수 있다.

---

# 33. AI 비용 최적화 도구 / 원칙

## 파일 검색

AI가 전체 Repository를 읽지 않게 한다.

사용:

- git diff
- ripgrep
- symbol search
- AST
- Project Registry

```text
전체 Repo
↓
관련 파일 후보
↓
필요한 파일만 AI Context
```

## Retry Limit

무한 반복 방지.

```text
최대 수정 3회
최대 Test Retry 3회
```

초과 시 실패 보고서를 남기고 종료.

## Context 최소화

로그 전체가 아니라:

```text
ERROR
Caused by
FAILED
```

주변만 추출해 전달.

## 단계적 모델 승격

```text
저비용 모델
↓ 실패
중간 모델
↓ 실패
고성능 모델
```

## 출력 최소화

작업 중 장황한 설명보다:

```text
변경 파일
변경 이유
테스트 결과
남은 문제
```

만 보고.

---

# 34. 도메인 명세 작성

도메인 명세는 한국어로 작성해도 된다.

추천 구조:

```text
도메인
Aggregate
Entity / Value Object
필드
불변식
상태 전이
권한
동시성
멱등성
트랜잭션
이벤트
실패 / 복구
Edge Case
Acceptance Criteria
결정 필요 사항
```

핵심:

```text
강한 모델
→ 무엇을 만들어야 하는지 정확히 설계

저렴한 모델
→ 확정된 명세를 코드로 변환
```

---

# 35. 테스트 작성 전략

AI 코딩에서는 핵심 도메인 규칙의 테스트를 먼저 만드는 방식이 유리하다.

```text
명세
↓
테스트 생성 Agent
↓
테스트 보호
↓
구현 Agent
↓
자동 검증
```

## 테스트 우회 방지

프롬프트가 아니라 CI로 강제한다.

예:

```text
구현 Agent 수정 허용:
src/main/**

수정 금지:
src/test/**
domain-spec/**
architecture-tests/**
```

테스트 변경 시 즉시 FAIL.

---

# 36. 권장 전체 검증 파이프라인

```text
               요구사항
                   ↓
           도메인 명세 작성
                   ↓
          독립적인 명세 검토
                   ↓
           Spec Traceability
                   ↓
       Unit / Property / Contract
                   ↓
              AI 구현
                   ↓
        build-convention
    ┌──────────────┼──────────────┐
    ↓              ↓              ↓
  PMD          SpotBugs        ArchUnit
    ↓              ↓              ↓
 Checkstyle      JaCoCo          PIT
                   ↓
                Semgrep
                   ↓
          Integration / E2E
                   ↓
         Performance Harness
          ├─ Load
          ├─ Stress
          ├─ Spike
          ├─ Breakpoint
          └─ Soak
                   ↓
               RiskGate
                   ↓
         PASS / REVIEW / BLOCK
                   ↓
               사람 리뷰
                   ↓
                 배포
                   ↓
            Runtime Metrics
```

---

# 37. 프로젝트별 책임 요약

## build-convention

```text
자동으로 확실히 검증 가능한 것
```

- 정적 분석
- 아키텍처 규칙
- Coverage
- Mutation
- Migration
- 테스트 구조 규칙
- 우회 방지

## RiskGate

```text
의미와 운영 맥락을 봐야 하는 것
```

- 동시성
- 멱등성
- 트랜잭션
- 데이터 정합성
- 성능
- 보안
- Breaking Change
- 운영 위험
- 최종 Risk Score

## DevForge

```text
실제 일을 수행하는 AI
```

- Slack / Issue 분석
- Project Resolver
- 모델 선택
- 코드 작성
- 테스트 작성
- 분석
- DB/Kafka 성능 실험
- PR 생성
- 결과 보고

---

# 38. 지금 기준 권장 구현 순서

```text
1. build-convention 유지
   ↓
2. build-convention 표준 JSON Report 생성
   ↓
3. RiskGate Spring Boot 프로젝트 생성
   ↓
4. Dummy RiskAnalyzer로 CI end-to-end 연결
   ↓
5. Semgrep CI 추가
   ↓
6. Jev RiskAnalyzer 연결
   ↓
7. Project Registry / Service Catalog
   ↓
8. Risk Audit DB
   ↓
9. DevForge
   ↓
10. Slack / GitHub 업무 입력 연동
   ↓
11. Model Router
   ↓
12. Property / Spec Traceability 강화
   ↓
13. DB Performance Harness
   ↓
14. Kafka / AWS Ephemeral Performance Lab
   ↓
15. Golden Eval Set
```

---

# 39. 핵심 철학

```text
AI가 생성한다.
도구가 검증한다.
정책이 결정한다.
사람이 최종 책임을 가진다.
```

그리고 가장 중요한 기준:

> **검증 가능한 일일수록 AI에게 더 과감하게 맡길 수 있다.**

AI 자동화의 생산성은 단순히 더 많은 코드를 만드는 데서 나오기보다,  
**틀린 결과를 자동으로 빠르게 잡을 수 있는 시스템을 얼마나 잘 구축했는가**에서 결정된다.
