# 파서 통합 테스트 fixture

이 디렉토리의 텍스트 파일들은 pdftotext 실행 결과를 모사한 fixture입니다.
실제 PDF 파일 대신 사용해 다음을 달성:

1. **개인정보 보호**: 실제 대회 PDF는 선수 실명이 포함돼 커밋 불가. 여기서는 가상의 이름 사용.
2. **CI 환경 단순화**: `pdftotext` 바이너리 의존성 제거.
3. **결정론적**: PDF 렌더링 차이/버전 차이 없음.

## 파일명 규칙

- `{scenario}.layout.txt` — `pdftotext -layout` 모드 출력 모사 (헤더 파싱에 사용)
- `{scenario}.raw.txt` — `pdftotext -raw` 모드 출력 모사 (결과 파싱에 사용, 개인전)
- 단체전(계주/팀DTT)은 layout만 사용.

## 시나리오

| 파일                         | 설명                                   |
|------------------------------|----------------------------------------|
| `individual_time_race.*`     | 일반 시간 경기 (500m+D 예선)           |
| `dtt_points_race.*`          | DTT 포인트 경기 (이름이 뒤, 정수 점수) |
| `team_relay.layout.txt`      | 단체전 계주 (layout 전용)              |