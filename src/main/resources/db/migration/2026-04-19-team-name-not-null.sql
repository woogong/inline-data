-- =====================================================================
-- competition_entry.team_name NULL → '' 백필 후 NOT NULL DEFAULT '' 로 변경
-- 작성일: 2026-04-19
-- 배경:
--   - 엔티티에서 team_name을 `@Column(nullable=false)`로 선언 (CompetitionEntry.normalizeTeamName)
--   - 기존 운영 DB에는 NULL 17행이 남아있어 Hibernate update가 MODIFY COLUMN 시도 시 실패
--   - MariaDB 10.1은 ALTER TABLE IF EXISTS 구문을 지원하지 않아 수동으로 적용
-- =====================================================================

UPDATE competition_entry SET team_name = '' WHERE team_name IS NULL;

ALTER TABLE competition_entry MODIFY team_name VARCHAR(100) NOT NULL DEFAULT '';