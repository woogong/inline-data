-- =====================================================================
-- EventHeat에 YouTube URL 컬럼 추가
-- 작성일: 2026-04-30
-- 적용 대상: MariaDB (ddl-auto: update로 자동 적용되지만 참고용으로 보관)
-- =====================================================================

ALTER TABLE event_heat
    ADD COLUMN youtube_url VARCHAR(500) NULL;
