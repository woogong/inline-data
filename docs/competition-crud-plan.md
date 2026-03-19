# 인라인 스피드 스케이팅 대회 정보 CRUD 구현 계획

## Context

참가요강 PDF(제45회 전국 남녀 종별 인라인 스피드대회)의 대회 개요 정보를 프로그램에서 관리하기 위한 CRUD를 구축한다. 현재 프로젝트는 Spring Boot 4.0.4 + JPA + MariaDB + Thymeleaf 스켈레톤 상태이며, 첫 번째 도메인 엔티티로 대회(Competition)를 추가한다.

---

## 1. Entity 설계: `Competition`

| PDF 필드 | Java 필드명 | 타입 | 비고 |
|---|---|---|---|
| PK | `id` | `Long` | `@GeneratedValue(IDENTITY)` |
| 대회명 | `name` | `String` | not null, length 200 |
| 대회기간 시작 | `startDate` | `LocalDate` | nullable |
| 대회기간 종료 | `endDate` | `LocalDate` | nullable |
| 대회일수 | `durationDays` | `Integer` | nullable |
| 대회장소 | `venue` | `String` | nullable, length 200 |
| 경기장 상세 | `venueDetail` | `String` | nullable, length 200 (예: "나주롤러경기장/200m 트랙") |
| 주최 | `host` | `String` | nullable, length 100 |
| 주관 | `organizer` | `String` | nullable, length 100 |
| 비고 | `notes` | `String` | TEXT |
| 생성일시 | `createdAt` | `LocalDateTime` | `@CreationTimestamp` |
| 수정일시 | `updatedAt` | `LocalDateTime` | `@UpdateTimestamp` |

- Setter 없이 `@Builder`로 생성, `update()` 메서드로 수정 (캡슐화)

---

## 2. 패키지 구조

```
kr.pe.batang.inlinedata/
├── InlinedataApplication.java              (기존)
├── config/JpaAuditingConfig.java
├── entity/Competition.java
├── repository/CompetitionRepository.java
├── service/CompetitionService.java
└── controller/
    ├── CompetitionController.java          (사용자: 목록, 상세)
    ├── AdminCompetitionController.java     (관리자: CRUD)
    └── CompetitionFormDto.java
```

---

## 3. Thymeleaf 페이지 및 URL 설계

### 사용자 페이지 (`CompetitionController`)

| HTTP | URL | 메서드 | 템플릿 | 설명 |
|---|---|---|---|---|
| GET | `/competitions` | `list()` | `competition/list.html` | 목록 |
| GET | `/competitions/{id}` | `detail(id)` | `competition/detail.html` | 상세 |

### 관리자 페이지 (`AdminCompetitionController`)

| HTTP | URL | 메서드 | 템플릿 | 설명 |
|---|---|---|---|---|
| GET | `/admin/competitions` | `list()` | `admin/competition/list.html` | 목록 |
| GET | `/admin/competitions/{id}` | `detail(id)` | `admin/competition/detail.html` | 상세 |
| GET | `/admin/competitions/new` | `createForm()` | `admin/competition/form.html` | 등록 폼 |
| POST | `/admin/competitions` | `create(dto)` | redirect → list | 등록 처리 |
| GET | `/admin/competitions/{id}/edit` | `editForm(id)` | `admin/competition/form.html` | 수정 폼 (공유) |
| POST | `/admin/competitions/{id}` | `update(id, dto)` | redirect → detail | 수정 처리 |
| POST | `/admin/competitions/{id}/delete` | `delete(id)` | redirect → list | 삭제 처리 |

- 등록/수정 폼은 `form.html` 하나로 공유 (id 유무로 분기)
- DELETE는 POST로 처리 (GET으로 삭제 방지)
- `@Valid` + `BindingResult`로 폼 유효성 검증

---

## 4. 생성/수정할 파일 목록

### Java 소스 (src/main/java/kr/pe/batang/inlinedata/)

1. `config/JpaAuditingConfig.java` — JPA Auditing 활성화
2. `entity/Competition.java` — JPA 엔티티
3. `repository/CompetitionRepository.java` — JPA Repository
4. `controller/CompetitionFormDto.java` — 폼 바인딩 DTO
5. `service/CompetitionService.java` — 비즈니스 로직
6. `controller/CompetitionController.java` — 사용자 컨트롤러 (목록, 상세)
7. `controller/AdminCompetitionController.java` — 관리자 컨트롤러 (CRUD)

### Thymeleaf 템플릿 (src/main/resources/templates/)

8. `layout/default.html` — 기본 레이아웃
9. `fragments/header.html` — 공통 네비게이션
10. `competition/list.html` — 사용자 대회 목록
11. `competition/detail.html` — 사용자 대회 상세
12. `admin/competition/list.html` — 관리자 대회 목록
13. `admin/competition/detail.html` — 관리자 대회 상세
14. `admin/competition/form.html` — 관리자 대회 등록/수정 폼

### 설정

15. `src/main/resources/application.yaml` — DB 연결, JPA 설정 추가 (기존 파일 수정)

### 테스트 (src/test/java/kr/pe/batang/inlinedata/)

16. `repository/CompetitionRepositoryTest.java`
17. `service/CompetitionServiceTest.java`
18. `controller/CompetitionControllerTest.java`
19. `controller/AdminCompetitionControllerTest.java`

---

## 5. 구현 순서

**Phase 1 — 기반 설정**
1. `application.yaml` DB/JPA 설정 추가
2. `JpaAuditingConfig.java` 생성

**Phase 2 — 도메인 코어**
3. `Competition.java` 엔티티
4. `CompetitionRepository.java`

**Phase 3 — 비즈니스 로직**
5. `CompetitionFormDto.java`
6. `CompetitionService.java`

**Phase 4 — 프레젠테이션**
7. 레이아웃/프래그먼트 템플릿
8. `CompetitionController.java`
9. `list.html`, `detail.html`, `form.html`

**Phase 5 — 검증**
10. 테스트 코드 작성
11. 애플리케이션 기동 후 PDF 데이터로 CRUD 수동 테스트

---

## 6. 검증 방법

1. `./gradlew bootRun`으로 애플리케이션 기동
2. 브라우저에서 `http://localhost:8080/competitions` 접속
3. 참가요강 PDF의 대회 정보를 등록 폼에 입력하여 Create 테스트
4. 목록 → 상세 → 수정 → 삭제 순서로 전체 CRUD 흐름 확인
5. `./gradlew test`로 자동화 테스트 실행
