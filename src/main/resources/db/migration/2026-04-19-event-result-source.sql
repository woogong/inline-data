-- =====================================================================
-- EventResult 출처 추적 + EventResultHistory 감사 테이블
-- 작성일: 2026-04-19
-- 적용 대상: 기존 MariaDB 10.1 서버 (ALTER TABLE IF EXISTS 미지원)
-- 배포 절차: 이 SQL을 먼저 실행한 뒤 애플리케이션 재시작
-- =====================================================================

-- 1) EventResult에 source / updated_at 컬럼 추가
--    기존 행은 모두 UPLOAD로 간주 (관리자 파일 업로드 경로로 적재되어 왔기 때문)
ALTER TABLE event_result
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'UPLOAD',
    ADD COLUMN updated_at DATETIME(6) NULL;

-- 2) 감사 테이블 (Hibernate가 CREATE TABLE로 이미 만들었다면 IF NOT EXISTS로 스킵)
CREATE TABLE IF NOT EXISTS event_result_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_result_id BIGINT       NOT NULL,
    heat_entry_id   BIGINT       NOT NULL,
    source          VARCHAR(10)  NOT NULL,
    ranking         INT          NULL,
    record          VARCHAR(30)  NULL,
    new_record      VARCHAR(20)  NULL,
    qualification   VARCHAR(10)  NULL,
    note            VARCHAR(100) NULL,
    recorded_at     DATETIME(6)  NULL,
    INDEX idx_erh_result_id (event_result_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;