# 경기 결과 PDF 자동 등록 구현 계획

## Context

이 프로젝트는 인라인 스피드 스케이팅 대회 정보를 관리하는 Spring Boot 백엔드이며, 종목/라운드/조/엔트리/결과까지 대부분의 기본 기능이 이미 구현되어 있다. 현재도 관리자 화면에서 결과 PDF를 수동 업로드하면 `ResultParsingService`가 PDF를 파싱해 `EventResult`를 저장하거나 갱신할 수 있다.

이번 작업의 목표는 경기 진행자가 경기 중 OneDrive에 결과 PDF를 업로드하면, 백엔드가 이를 자동으로 인식해 기존 파서를 재사용하고 결과 테이블을 자동 갱신하도록 만드는 것이다.

---

## 1. 현재 코드 기준 분석

### 1.1 이미 구현된 것

- `src/main/java/kr/pe/batang/inlinedata/service/ResultParsingService.java`
  - PDF 텍스트 추출 후 경기번호(`eventNumber`), 조번호(`heatNumber`)를 해석한다.
  - `EventRound`를 경기번호로 찾고, `EventHeat`를 찾거나 생성한다.
  - 기존 `HeatEntry`를 배번으로 매칭하고, 없으면 `CompetitionEntry`와 `HeatEntry`를 자동 생성한다.
  - `EventResult`는 `heat_entry_id` 기준으로 upsert 된다.
  - DTT 종목은 결과 저장 후 전체 기록 순으로 재정렬한다.
- `src/main/java/kr/pe/batang/inlinedata/controller/AdminEventController.java`
  - 관리자 화면에서 결과 PDF를 수동 업로드하는 API가 이미 있다.
- `src/main/java/kr/pe/batang/inlinedata/service/PdfTextExtractor.java`
  - `pdftotext -layout` 기반으로 PDF 텍스트를 추출한다.

### 1.2 현재 구조의 한계

- 자동 감시 기능이 없다.
- 어떤 PDF를 이미 처리했는지 저장하는 이력 테이블이 없다.
- 업로드 중인 파일과 업로드 완료 파일을 구분하는 안정장치가 없다.
- 실패 파일을 재시도하거나 운영자가 확인할 수 있는 상태 관리가 없다.
- `competitionId`는 현재 요청 경로에서만 전달되므로, 자동 등록 시 어떤 대회에 반영할지 별도 규칙이 필요하다.

---

## 2. 구현 방향

### 2.1 권장 방식

1차 구현은 OneDrive API 직접 연동이 아니라, 서버 또는 운영 PC에 동기화된 OneDrive 로컬 폴더를 주기적으로 스캔하는 방식으로 간다.

이 방식이 현재 구조에 맞는 이유:

- 기존 파서는 `Path` 기반이라 로컬 파일 처리와 바로 연결된다.
- 별도 OAuth, 토큰 갱신, Graph API 호출, 원격 파일 다운로드 계층을 새로 만들 필요가 없다.
- 경기 중 운영 안정성이 더 중요하므로, 네트워크 연동보다 로컬 동기화 폴더 감시가 단순하고 장애 지점이 적다.

단, 실제 운영 환경이 OneDrive 웹 업로드만 가능하고 서버에 동기화 클라이언트를 둘 수 없다면 2차 단계에서 Microsoft Graph API 연동으로 확장한다.

---

## 3. 목표 아키텍처

### 3.1 처리 흐름

1. 운영자가 결과 PDF를 OneDrive 지정 폴더에 업로드한다.
2. OneDrive 동기화 클라이언트가 해당 파일을 서버 로컬 폴더로 동기화한다.
3. 백엔드 스케줄러가 지정 폴더를 주기적으로 스캔한다.
4. 처리 대상 PDF인지 판별한다.
5. 파일 크기/수정 시각이 안정화된 파일만 가져온다.
6. 처리 이력 테이블에서 이미 처리한 파일인지 확인한다.
7. `ResultParsingService.parseResultPdf(path, competitionId)`를 호출한다.
8. 성공 시 처리 결과와 파일 식별값을 이력 테이블에 저장한다.
9. 실패 시 실패 사유와 재시도 횟수를 저장하고 로그를 남긴다.
10. 관리자 화면에서 최근 자동 등록 내역과 실패 파일을 확인한다.

### 3.2 핵심 원칙

- 파싱 로직 자체는 최대한 건드리지 않고 재사용한다.
- 자동 등록은 반드시 idempotent 해야 한다.
- 동일 파일 재처리 여부는 파일명만이 아니라 `경로 + 크기 + 마지막 수정 시각` 또는 `SHA-256 해시` 기준으로 판단한다.
- 업로드 도중인 파일은 처리하지 않는다.
- 실패를 무시하지 않고 운영자가 볼 수 있어야 한다.

---

## 4. 도메인 및 설정 설계

### 4.1 신규 설정

`application.yaml` 또는 프로파일별 설정에 자동 등록용 설정을 추가한다.

예시:

```yaml
app:
  result-import:
    auto-enabled: false
    watch-dir: /data/inlinedata/result-pdfs
    polling-interval-ms: 10000
    stable-wait-seconds: 15
    competition-id: 1
    include-pattern: "*.pdf"
    archive-dir: /data/inlinedata/result-pdfs/archive
    error-dir: /data/inlinedata/result-pdfs/error
```

설명:

- `auto-enabled`: 자동 수집 활성화 여부
- `watch-dir`: OneDrive 동기화 대상 로컬 폴더
- `polling-interval-ms`: 주기 스캔 간격
- `stable-wait-seconds`: 마지막 수정 후 이 시간 이상 지나야 처리
- `competition-id`: 자동 반영 대상 대회
- `archive-dir`: 성공 파일 이동 경로
- `error-dir`: 실패 파일 이동 경로

### 4.2 신규 엔티티 제안

`ResultImportFile` 같은 처리 이력 엔티티를 추가한다.

권장 필드:

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `Long` | PK |
| `competitionId` | `Long` | 대상 대회 |
| `fileName` | `String` | 원본 파일명 |
| `filePath` | `String` | 처리 당시 절대/상대 경로 |
| `fileSize` | `Long` | 파일 크기 |
| `fileHash` | `String` | 중복 판별용 해시 |
| `lastModifiedAt` | `LocalDateTime` | 원본 파일 수정 시각 |
| `status` | `String` | `PENDING/SUCCESS/FAILED/SKIPPED` |
| `resultsCount` | `Integer` | 저장/갱신 결과 건수 |
| `newEntriesCount` | `Integer` | 새 엔트리 생성 건수 |
| `message` | `String` | 실패 또는 스킵 사유 |
| `processedAt` | `LocalDateTime` | 처리 완료 시각 |
| `createdAt` | `LocalDateTime` | 생성 시각 |
| `updatedAt` | `LocalDateTime` | 수정 시각 |

중복 방지는 `fileHash` 유니크 또는 `competitionId + fileHash` 유니크를 권장한다.

---

## 5. 구현 상세 계획

### Phase 1. 자동 등록 기반 추가

생성/수정 대상:

- `config` 또는 `service` 하위에 자동 등록 설정 클래스 추가
- `InlinedataApplication.java`에 `@EnableScheduling` 추가
- `ResultImportFile.java`
- `ResultImportFileRepository.java`

구현 내용:

- 설정 프로퍼티 바인딩 클래스 추가
- 처리 이력 엔티티/리포지토리 추가
- 최근 처리 목록 조회용 기본 쿼리 준비

### Phase 2. 파일 스캔 및 안전 처리 서비스

생성/수정 대상:

- `AutoResultImportService.java` 신규
- 필요 시 `ResultParsingService.java` 소폭 보완

구현 내용:

- `watch-dir`에서 PDF 목록 조회
- 파일 크기 0, 임시 확장자, 최근 수정 파일 제외
- 해시 계산 후 이미 처리된 파일인지 확인
- 파일별 단건 처리 메서드와 디렉터리 일괄 처리 메서드 분리
- 처리 성공 시 archive 이동, 실패 시 error 이동 여부 결정

권장 메서드 구조:

- `scanAndImport()`
- `findCandidateFiles()`
- `isStableFile(Path path)`
- `importFile(Path path, Long competitionId)`
- `archiveProcessedFile(...)`
- `markFailed(...)`

### Phase 3. 결과 파서 재사용 구조 정리

현재 `ResultParsingService`는 자동 등록에도 그대로 활용 가능하다. 다만 다음 보완이 있으면 운영성이 좋아진다.

- 파싱 실패/스킵 사유를 더 명확히 반환
- 경기번호를 찾지 못한 경우, 대상 경기 없음, 결승종합 스킵 등을 구분
- 로그에 파일명, 경기번호, 조번호, 저장 건수를 함께 남기기

가능하면 `ImportResult`를 확장해 아래 성격을 표현한다.

- `processed`
- `skipped`
- `reason`

### Phase 4. 관리자 확인 화면

생성/수정 대상:

- `AdminEventController.java` 또는 별도 `AdminResultImportController.java`
- `templates/admin/...` 하위 최근 자동 등록 내역 화면

구현 내용:

- 최근 자동 등록 목록
- 성공/실패/스킵 상태와 메시지 표시
- 실패 파일 재처리 버튼
- 자동 등록 즉시 실행 버튼

최소 범위에서는 대회별 이벤트 목록 화면에 “자동 등록 상태” 영역을 추가하는 정도로도 충분하다.

### Phase 5. 운영 보조 기능

선택 기능이지만 실제 경기 운영에는 유용하다.

- 수동 재스캔 API
- 실패 파일 재처리 API
- 특정 파일 강제 재처리 API
- 최근 N분 내 처리 성공/실패 건수 요약

---

## 6. 기존 코드와의 연결 포인트

### 6.1 재사용 대상

- `ResultParsingService.parseResultPdf(Path pdfPath, Long competitionId)`
- `PdfTextExtractor.extractText(Path pdfPath)`
- `EventResultRepository.findByHeatEntryId(...)`
- `EventRoundRepository.findByEvent_CompetitionIdOrderByEventNumberAsc(...)`

### 6.2 수정이 필요한 부분

- `InlinedataApplication.java`
  - 스케줄링 활성화
- `application.yaml`, `application-local.yaml`, `application-prod.yaml`
  - 자동 등록 관련 설정 추가
- `AdminEventController.java`
  - 운영자가 자동 등록 상태를 확인하거나 수동 재실행할 수 있는 API 추가

### 6.3 주의할 현재 제약

- 자동 등록은 현재 구조상 `competitionId`가 고정돼야 한다.
- 파일명으로 대회를 판별하는 로직은 아직 없으므로, 우선은 “현재 운영 중인 1개 대회” 기준으로 설계하는 것이 안전하다.
- 동일 경기 PDF가 수정되어 재업로드될 가능성이 있으므로 “처리된 파일명 중복”이 아니라 “파일 버전 중복”을 구분해야 한다.

---

## 7. 운영 시나리오 기준 예외 처리

### 7.1 부분 업로드 파일

OneDrive 동기화 중인 파일은 크기가 계속 변할 수 있다. 따라서 마지막 수정 시각이 충분히 지난 파일만 처리해야 한다.

### 7.2 같은 경기 결과의 재업로드

기록 정정본이 다시 올라올 수 있다. 이 경우:

- 파일 해시가 다르면 재처리 허용
- 결과 저장은 기존 `EventResult` update 로직이 있으므로 덮어쓰기 가능
- 단, “언제 어떤 파일로 덮어썼는지” 이력은 남겨야 한다

### 7.3 파싱은 성공했지만 결과가 0건인 경우

이 경우도 성공으로 볼지 스킵으로 볼지 기준을 정해야 한다. 권장 기준:

- `결승종합`처럼 의도적 무시는 `SKIPPED`
- 경기번호 미인식, 매칭 실패는 `FAILED`
- 파싱 완료했으나 저장 0건은 `SKIPPED` 또는 `FAILED` 중 운영 기준에 맞게 선택

### 7.4 잘못된 대회 폴더 연결

다른 대회의 결과 PDF가 들어오면 경기번호는 같아도 잘못된 라운드에 반영될 수 있다. 따라서 운영 중인 대회마다 감시 폴더를 분리하거나, 최소한 대회별 설정을 명시적으로 분리해야 한다.

---

## 8. 테스트 계획

### 8.1 단위 테스트

- `ResultImportFileRepository` 기본 조회 테스트
- `AutoResultImportService`
  - 신규 파일 감지
  - 이미 처리한 파일 스킵
  - 안정화되지 않은 파일 스킵
  - 파싱 성공 시 이력 저장
  - 파싱 실패 시 실패 이력 저장

### 8.2 통합 테스트

- 테스트 리소스 PDF 또는 목 파서를 사용해 자동 스캔 후 `EventResult`가 저장되는지 검증
- 동일 파일 재스캔 시 중복 처리되지 않는지 검증
- 수정된 다른 파일 해시로 재업로드 시 결과가 갱신되는지 검증

### 8.3 수동 검증

1. 로컬에 OneDrive 동기화 폴더와 동일한 역할의 테스트 디렉터리 준비
2. 자동 등록 활성화 후 PDF 투입
3. 일정 시간 내 결과가 `event_result`에 반영되는지 확인
4. 실패 파일이 관리자 화면과 로그에 노출되는지 확인
5. 수정본 PDF 재업로드 시 결과가 갱신되는지 확인

---

## 9. 파일 단위 작업 목록

### 신규 생성 후보

- `src/main/java/kr/pe/batang/inlinedata/config/ResultImportProperties.java`
- `src/main/java/kr/pe/batang/inlinedata/entity/ResultImportFile.java`
- `src/main/java/kr/pe/batang/inlinedata/repository/ResultImportFileRepository.java`
- `src/main/java/kr/pe/batang/inlinedata/service/AutoResultImportService.java`
- `src/test/java/.../service/AutoResultImportServiceTest.java`

### 수정 후보

- `src/main/java/kr/pe/batang/inlinedata/InlinedataApplication.java`
- `src/main/java/kr/pe/batang/inlinedata/service/ResultParsingService.java`
- `src/main/java/kr/pe/batang/inlinedata/controller/AdminEventController.java`
- `src/main/resources/application.yaml`
- `src/main/resources/application-local.yaml`
- `src/main/resources/application-prod.yaml`
- `src/main/resources/templates/admin/event/list.html`

---

## 10. 권장 구현 순서

1. 자동 등록 설정 클래스와 스케줄링 활성화 추가
2. 처리 이력 엔티티/리포지토리 추가
3. 폴더 스캔 및 단건 처리 서비스 구현
4. 기존 `ResultParsingService`의 결과 반환 구조 보강
5. 관리자 확인 화면 및 재처리 API 추가
6. 테스트 작성 및 실데이터로 운영 검증

---

## 11. 최종 제안

이번 기능은 “PDF 파서 개선”보다 “운영 가능한 자동 수집 파이프라인 추가”가 본질이다. 현재 코드베이스에서는 다음 범위로 1차 구현하는 것이 가장 현실적이다.

- OneDrive 동기화 로컬 폴더 주기 스캔
- 처리 이력 저장
- 중복 방지
- 실패/재처리 관리
- 기존 `ResultParsingService` 재사용

이 범위만 먼저 완성해도 경기 중 업로드된 결과 PDF를 거의 실시간으로 결과 테이블에 반영할 수 있고, 이후 필요하면 Microsoft Graph API 연동이나 대회별 다중 폴더 운영으로 확장할 수 있다.
