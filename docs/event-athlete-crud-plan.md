# 종목 및 선수 CRUD 구현 계획

## Context

조편성 PDF(제45회 전국 남녀 종별 인라인 스피드대회)를 분석하여, 대회(Competition) 하위에 종목(Event)과 선수(Athlete) 데이터를 관리하기 위한 CRUD를 구축한다. 기존 Competition CRUD 위에 확장하는 형태로 구현한다.

---

## 1. PDF 데이터 분석 결과

### 1.1 부별 (Division)

성별 + 연령대 + 레벨로 구분된다.

| 성별 | 연령대 | 예시 |
|---|---|---|
| 여 | 초등부 1,2학년 / 3,4학년 / 5,6학년 | 여초부 5,6학년 |
| 남 | 초등부 1,2학년 / 3,4학년 / 5,6학년 | 남초부 3,4학년 |
| 여/남 | 중등부 | 여중부, 남중부 |
| 여/남 | 고등부 | 여고부, 남고부 |
| 여/남 | 대학부 | 여대부, 남대부 |
| 여/남 | 일반부 | 여일부, 남일부 |
| 여/남 | 초등부 일반(B조) 1,2학년 / 3,4학년 / 5,6학년 | 여초부 일반(B조) 5,6학년 |
| 여/남 | 초등부 일반(B조) | 남초부 일반(B조) 5,6학년 |

### 1.2 종목 (Event Distance/Type)

| 종목명 | 설명 |
|---|---|
| 500m+D | 500m+D |
| 300m | 300미터 |
| 200m | 200미터 |
| 1,000m | 1000미터 |
| DTT200m | 듀얼 타임 트라이얼 200미터 |
| E10,000m | 제외 10,000미터 |
| EP1,600m | 제외 포인트 1,600미터 |
| EP5,000m | 제외 포인트 5,000미터 |
| P3,000m | 포인트 3,000미터 |
| P5,000m | 포인트 5,000미터 |
| E3,000m | 제외 3,000미터 |
| 계주3,000m | 계주 3,000미터 (팀) |
| 계주2,000m | 계주 2,000미터 (팀) |

### 1.3 라운드 (Round)

| 라운드 | 영문 |
|---|---|
| 예선 | Preliminary |
| 준준결승 | Quarter-final |
| 준결승 | Semi-final |
| 결승 | Final |
| 조별결승 | Group Final |

### 1.4 선수 정보 구조

선수는 특정 대회에 종속되지 않고 지속적으로 관리되는 마스터 데이터이다. 대회 참가 정보는 별도로 관리한다.

**선수 기본 정보 (대회 독립):**
- **이름(Name)**: 선수명 (예: 구예림)
- **성별(Gender)**: M/F
- **소속(Team)**: 팀/학교/클럽 (예: 팀에스, 논산내동초등학교 등)

**대회 참가 정보 (대회별):**
- **번호(Bib)**: 종목별로 부여되는 배번 (예: 4)
- **소속(Team)**: 출전 시 소속 팀 (이적 등으로 변경될 수 있음, 무소속 가능)
- **학년/기수(Grade)**: 소속명 뒤에 붙는 숫자 (예: 6 → 6학년)

### 1.5 경기 번호 체계

PDF의 경기는 1번부터 152번까지 순번이 매겨져 있으며, 3일간 진행된다:
- 제1일차 (2026.3.20 금): 1~49번
- 제2일차 (2026.3.21 토): 50~104번
- 제3일차 (2026.3.22 일): 105~152번

---

## 2. 도메인 모델 설계

### 2.1 Entity 관계도

```
Competition (대회)
 ├── CompetitionEntry (대회 참가 선수)
 │    ├── Athlete (선수) ── 마스터 데이터
 │    └── Team (소속) ── nullable
 └── Event (종목/경기)
      └── EventHeat (조)
           └── HeatEntry (출전 엔트리)
                └── CompetitionEntry (대회 참가 선수)
```

### 2.2 Entity 상세

#### Team (소속/팀)

| 필드 | Java 필드명 | 타입 | 비고 |
|---|---|---|---|
| PK | `id` | `Long` | `@GeneratedValue(IDENTITY)` |
| 소속명 | `name` | `String` | not null, length 100 (예: 팀에스, THE LAP) |
| 시도 | `region` | `String` | not null, length 20 (예: 경기, 충남, 부산) |
| 생성일시 | `createdAt` | `LocalDateTime` | `@CreatedDate` |
| 수정일시 | `updatedAt` | `LocalDateTime` | `@LastModifiedDate` |

#### Athlete (선수) — 마스터 데이터

대회와 무관하게 지속적으로 관리되는 선수 기본 정보.

| 필드 | Java 필드명 | 타입 | 비고 |
|---|---|---|---|
| PK | `id` | `Long` | `@GeneratedValue(IDENTITY)` |
| 이름 | `name` | `String` | not null, length 50 |
| 성별 | `gender` | `String` | not null (M/F) |
| 생성일시 | `createdAt` | `LocalDateTime` | `@CreatedDate` |
| 수정일시 | `updatedAt` | `LocalDateTime` | `@LastModifiedDate` |

#### CompetitionEntry (대회 참가 정보)

특정 대회에 참가하는 선수의 대회별 정보. 이적, 학년 변동 등을 반영한다.

| 필드 | Java 필드명 | 타입 | 비고 |
|---|---|---|---|
| PK | `id` | `Long` | `@GeneratedValue(IDENTITY)` |
| 대회 | `competition` | `Competition` | `@ManyToOne`, FK |
| 선수 | `athlete` | `Athlete` | `@ManyToOne`, FK |
| 소속 | `team` | `Team` | `@ManyToOne`, FK, nullable (무소속 가능) |
| 학년/기수 | `grade` | `Integer` | nullable (예: 6 → 6학년 또는 기수) |
| 생성일시 | `createdAt` | `LocalDateTime` | `@CreatedDate` |
| 수정일시 | `updatedAt` | `LocalDateTime` | `@LastModifiedDate` |

- unique 제약: (competition, athlete) 조합

#### Event (종목/경기)

대회 내에서 하나의 경기를 나타낸다. (예: "1 여초부 5,6학년 500m+D 예선")

| 필드 | Java 필드명 | 타입 | 비고 |
|---|---|---|---|
| PK | `id` | `Long` | `@GeneratedValue(IDENTITY)` |
| 대회 | `competition` | `Competition` | `@ManyToOne`, FK |
| 경기번호 | `eventNumber` | `Integer` | not null (PDF 순번: 1~152) |
| 부별명 | `divisionName` | `String` | not null, length 50 (예: 여초부 5,6학년) |
| 성별 | `gender` | `String` | not null (M/F/X) X=혼성 |
| 종목명 | `eventName` | `String` | not null, length 30 (예: 500m+D, DTT200m) |
| 라운드 | `round` | `String` | nullable, length 20 (예선/준준결승/준결승/결승/조별결승) |
| 대회일차 | `dayNumber` | `Integer` | nullable (1, 2, 3) |
| 생성일시 | `createdAt` | `LocalDateTime` | `@CreatedDate` |
| 수정일시 | `updatedAt` | `LocalDateTime` | `@LastModifiedDate` |

- unique 제약: (competition, eventNumber)

#### EventHeat (조)

| 필드 | Java 필드명 | 타입 | 비고 |
|---|---|---|---|
| PK | `id` | `Long` | `@GeneratedValue(IDENTITY)` |
| 종목 | `event` | `Event` | `@ManyToOne`, FK |
| 조번호 | `heatNumber` | `Integer` | not null (1, 2, 3...) |
| 생성일시 | `createdAt` | `LocalDateTime` | `@CreatedDate` |

- 결승처럼 조 구분이 없는 경우 heatNumber = 0

#### HeatEntry (출전 엔트리)

| 필드 | Java 필드명 | 타입 | 비고 |
|---|---|---|---|
| PK | `id` | `Long` | `@GeneratedValue(IDENTITY)` |
| 조 | `heat` | `EventHeat` | `@ManyToOne`, FK |
| 대회참가선수 | `entry` | `CompetitionEntry` | `@ManyToOne`, FK |
| 배번 | `bibNumber` | `Integer` | not null (종목 내 선수 번호) |

---

## 3. 구현 범위 및 우선순위

전체를 한번에 구현하면 복잡하므로, 단계별로 나누어 진행한다.

### Phase 1: Team(소속) CRUD

기본 마스터 데이터로서 선수보다 먼저 구현한다.

**생성/수정 파일:**
- `entity/Team.java`
- `repository/TeamRepository.java`
- `service/TeamService.java`
- `controller/dto/TeamFormDto.java`
- `controller/AdminTeamController.java`
- `templates/admin/team/list.html`
- `templates/admin/team/detail.html`
- `templates/admin/team/form.html`

**주요 기능:**
- 소속 목록 조회 (시도별 필터링)
- 소속 등록/수정/삭제
- 중복 체크 (name + region)

### Phase 2: Athlete(선수) CRUD

**생성/수정 파일:**
- `entity/Athlete.java`
- `repository/AthleteRepository.java`
- `service/AthleteService.java`
- `controller/dto/AthleteFormDto.java`
- `controller/AdminAthleteController.java`
- `controller/AthleteController.java`
- `templates/admin/athlete/list.html`
- `templates/admin/athlete/detail.html`
- `templates/admin/athlete/form.html`
- `templates/athlete/list.html`
- `templates/athlete/detail.html`

**주요 기능:**
- 선수 목록 조회 (소속별, 시도별, 이름 검색)
- 선수 등록 시 소속(Team) 선택
- 선수 상세: 참가 종목 이력 표시 (Phase 3 이후)

### Phase 3: Event(종목) CRUD

**생성/수정 파일:**
- `entity/Event.java`
- `entity/EventHeat.java`
- `entity/HeatEntry.java`
- `repository/EventRepository.java`
- `repository/EventHeatRepository.java`
- `repository/HeatEntryRepository.java`
- `service/EventService.java`
- `controller/dto/EventFormDto.java`
- `controller/AdminEventController.java`
- `controller/EventController.java`
- `templates/admin/event/list.html`
- `templates/admin/event/detail.html`
- `templates/admin/event/form.html`
- `templates/event/list.html`
- `templates/event/detail.html`

**주요 기능:**
- 대회별 종목 목록 (일차별, 부별, 거리별 필터링)
- 종목 등록: 부별 + 거리 + 라운드 조합
- 조편성: 종목 내 조(Heat) 생성 및 선수 배정
- 종목 상세: 조별 출전 선수 목록 표시

---

## 4. URL 설계

### 4.1 Team (소속) 관리

| HTTP | URL | 설명 |
|---|---|---|
| GET | `/admin/teams` | 소속 목록 |
| GET | `/admin/teams/{id}` | 소속 상세 |
| GET | `/admin/teams/new` | 소속 등록 폼 |
| POST | `/admin/teams` | 소속 등록 처리 |
| GET | `/admin/teams/{id}/edit` | 소속 수정 폼 |
| POST | `/admin/teams/{id}` | 소속 수정 처리 |
| POST | `/admin/teams/{id}/delete` | 소속 삭제 처리 |

### 4.2 Athlete (선수) 관리

| HTTP | URL | 설명 |
|---|---|---|
| GET | `/athletes` | 선수 목록 (사용자) |
| GET | `/athletes/{id}` | 선수 상세 (사용자) |
| GET | `/admin/athletes` | 선수 목록 (관리자) |
| GET | `/admin/athletes/{id}` | 선수 상세 (관리자) |
| GET | `/admin/athletes/new` | 선수 등록 폼 |
| POST | `/admin/athletes` | 선수 등록 처리 |
| GET | `/admin/athletes/{id}/edit` | 선수 수정 폼 |
| POST | `/admin/athletes/{id}` | 선수 수정 처리 |
| POST | `/admin/athletes/{id}/delete` | 선수 삭제 처리 |

### 4.3 Event (종목) 관리

| HTTP | URL | 설명 |
|---|---|---|
| GET | `/competitions/{compId}/events` | 종목 목록 (사용자) |
| GET | `/competitions/{compId}/events/{id}` | 종목 상세 (사용자) |
| GET | `/admin/competitions/{compId}/events` | 종목 목록 (관리자) |
| GET | `/admin/competitions/{compId}/events/{id}` | 종목 상세 (관리자) |
| GET | `/admin/competitions/{compId}/events/new` | 종목 등록 폼 |
| POST | `/admin/competitions/{compId}/events` | 종목 등록 처리 |
| GET | `/admin/competitions/{compId}/events/{id}/edit` | 종목 수정 폼 |
| POST | `/admin/competitions/{compId}/events/{id}` | 종목 수정 처리 |
| POST | `/admin/competitions/{compId}/events/{id}/delete` | 종목 삭제 처리 |

---

## 5. 패키지 구조 (최종)

```
kr.pe.batang.inlinedata/
├── InlinedataApplication.java
├── config/
│   └── JpaAuditingConfig.java
├── entity/
│   ├── Competition.java          (기존)
│   ├── Team.java                 (Phase 1)
│   ├── Athlete.java              (Phase 2)
│   ├── Event.java                (Phase 3)
│   ├── EventHeat.java            (Phase 3)
│   └── HeatEntry.java            (Phase 3)
├── repository/
│   ├── CompetitionRepository.java (기존)
│   ├── TeamRepository.java       (Phase 1)
│   ├── AthleteRepository.java    (Phase 2)
│   ├── EventRepository.java      (Phase 3)
│   ├── EventHeatRepository.java  (Phase 3)
│   └── HeatEntryRepository.java  (Phase 3)
├── service/
│   ├── CompetitionService.java   (기존)
│   ├── TeamService.java          (Phase 1)
│   ├── AthleteService.java       (Phase 2)
│   └── EventService.java         (Phase 3)
└── controller/
    ├── CompetitionController.java      (기존)
    ├── AdminCompetitionController.java (기존)
    ├── dto/
    │   ├── CompetitionFormDto.java     (기존)
    │   ├── TeamFormDto.java            (Phase 1)
    │   ├── AthleteFormDto.java         (Phase 2)
    │   └── EventFormDto.java           (Phase 3)
    ├── AdminTeamController.java        (Phase 1)
    ├── AthleteController.java          (Phase 2)
    ├── AdminAthleteController.java     (Phase 2)
    ├── EventController.java            (Phase 3)
    └── AdminEventController.java       (Phase 3)
```

---

## 6. 선수 데이터 정규화 참고

PDF에서 추출한 선수 정보의 패턴:

```
번호  이름 (시도 소속명학년)
예: 4   구예림 (경기 팀에스6)
```

파싱 규칙:
- `(` 와 `)` 사이를 추출
- 첫 번째 공백까지가 **시도**
- 나머지에서 맨 뒤 숫자가 **학년/기수**
- 숫자를 제외한 부분이 **소속명**

시도 목록 (17개 시도):
- 서울, 부산, 대구, 인천, 광주, 대전, 울산, 세종
- 경기, 강원, 충북, 충남, 전북, 전남, 경북, 경남, 제주

---

## 7. 구현 순서 상세

### Phase 1: Team CRUD (소속 관리)

1. `Team.java` 엔티티 생성
2. `TeamRepository.java` 생성
3. `TeamFormDto.java` 생성
4. `TeamService.java` 생성
5. `AdminTeamController.java` 생성
6. 템플릿 3개 (list, detail, form) 생성
7. 네비게이션에 소속 관리 메뉴 추가
8. 테스트 코드 작성

### Phase 2: Athlete CRUD (선수 관리)

1. `Athlete.java` 엔티티 생성
2. `AthleteRepository.java` 생성
3. `AthleteFormDto.java` 생성
4. `AthleteService.java` 생성
5. `AdminAthleteController.java` 생성
6. `AthleteController.java` 생성 (사용자 조회)
7. 관리자 템플릿 3개 + 사용자 템플릿 2개 생성
8. 네비게이션에 선수 메뉴 추가
9. 테스트 코드 작성

### Phase 3: Event CRUD (종목/경기 관리)

1. `Event.java`, `EventHeat.java`, `HeatEntry.java` 엔티티 생성
2. Repository 3개 생성
3. `EventFormDto.java` 생성
4. `EventService.java` 생성
5. `AdminEventController.java` 생성
6. `EventController.java` 생성 (사용자 조회)
7. 관리자 템플릿 3개 + 사용자 템플릿 2개 생성
8. 대회 상세 페이지에서 종목 목록 링크 추가
9. 테스트 코드 작성

---

## 8. 검증 방법

1. Phase별로 `./gradlew bootRun` 후 브라우저에서 CRUD 확인
2. PDF 데이터를 기반으로 실제 데이터 입력 테스트:
   - 소속 등록: "팀에스 (경기)", "THE LAP (부산)", "논산내동초등학교 (충남)" 등
   - 선수 등록: "구예림 (경기 팀에스 6학년)" 등
   - 종목 등록: "1 여초부 5,6학년 500m+D 예선" → 조편성 → 선수 배정
3. `./gradlew test`로 자동화 테스트 실행

---

## 9. 향후 확장 고려사항

- **대회 결과 관리**: 각 종목별 순위/기록 저장 (Result 엔티티)
- **PDF 파싱 자동화**: 조편성 PDF 업로드 시 자동으로 종목/선수 데이터 생성
- **검색/필터**: 선수 이름, 소속, 시도별 검색 기능 강화
- **통계**: 선수별 출전 이력, 소속별 선수 수 등
