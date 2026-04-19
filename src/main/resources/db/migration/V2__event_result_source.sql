-- =====================================================================
-- EventResult 출처 추적 + EventResultHistory 감사 테이블
-- =====================================================================
-- 이전 배포에서 ddl-auto: update가 ALTER TABLE IF EXISTS 구문으로 실패해
-- 일부 컬럼/테이블이 이미 부분 생성되었을 수 있어 전 구문에 IF NOT EXISTS 사용.
-- =====================================================================

ALTER TABLE event_result
    ADD COLUMN IF NOT EXISTS source VARCHAR(10) NOT NULL DEFAULT 'UPLOAD',
    ADD COLUMN IF NOT EXISTS updated_at DATETIME(6) NULL;

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