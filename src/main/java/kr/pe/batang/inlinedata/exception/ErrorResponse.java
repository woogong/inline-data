package kr.pe.batang.inlinedata.exception;

/**
 * 글로벌 예외 핸들러가 JSON 응답으로 반환하는 표준 에러 포맷.
 */
public record ErrorResponse(String status, String code, String message) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse("error", code, message != null ? message : "요청 처리 중 오류가 발생했습니다.");
    }
}