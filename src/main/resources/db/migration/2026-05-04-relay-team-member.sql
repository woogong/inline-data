-- =====================================================================
-- 계주 팀 구성 선수 지원
-- 작성일: 2026-05-04
-- =====================================================================

-- 1) event 테이블에 relay_event 컬럼 추가
ALTER TABLE event
    ADD COLUMN relay_event BOOLEAN NOT NULL DEFAULT FALSE;

-- 2) relay_team_member 테이블 생성
CREATE TABLE IF NOT EXISTS relay_team_member (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    heat_entry_id BIGINT      NOT NULL,
    order_number  TINYINT     NOT NULL DEFAULT 0,
    athlete_name  VARCHAR(50) NOT NULL,
    athlete_id    BIGINT      NULL,
    created_at    DATETIME(6) NULL,
    CONSTRAINT FK_rtm_heat_entry FOREIGN KEY (heat_entry_id) REFERENCES heat_entry(id) ON DELETE CASCADE,
    CONSTRAINT FK_rtm_athlete    FOREIGN KEY (athlete_id)    REFERENCES athlete(id) ON DELETE SET NULL,
    INDEX idx_rtm_heat_entry (heat_entry_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
