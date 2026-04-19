package kr.pe.batang.inlinedata.entity;

/**
 * 경기 결과의 출처.
 *
 * 우선순위 정책:
 * - {@link #MANUAL}, {@link #UPLOAD}는 동순위(high). 서로를 덮어쓸 수 있다.
 * - {@link #AUTO}는 하순위. high 소스가 기록한 행을 덮어쓸 수 없다.
 *
 * 감사 로그용으로는 세 값을 구분해 저장하지만, overwrite 판정에서는 MANUAL/UPLOAD를 동일 레벨로 본다.
 */
public enum ResultSource {
    /** 관리자가 UI에서 직접 수정한 결과. */
    MANUAL,
    /** 관리자가 파일 업로드로 등록한 결과. */
    UPLOAD,
    /** OneDrive 등 자동 스캔으로 등록된 결과. */
    AUTO;

    /** 이 source가 other source가 기록한 행을 덮어쓸 수 있는지 판정. */
    public boolean canOverwrite(ResultSource other) {
        if (other == null) return true;
        // AUTO는 MANUAL/UPLOAD를 덮어쓸 수 없음. 그 외에는 가능.
        return !(this == AUTO && (other == MANUAL || other == UPLOAD));
    }
}