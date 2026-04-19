package kr.pe.batang.inlinedata.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 애플리케이션 전역 예외 핸들러.
 *
 * 응답 포맷은 핸들러 종류에 따라 분기:
 * - JSON (@RestController 또는 @ResponseBody 메서드): {@link ErrorResponse} body + 상태 코드
 * - View (@Controller): 이전 페이지로 redirect하면서 flash attribute에 errorMessage 설정.
 *   Referer가 없으면 공용 error.html로 폴백.
 *
 * 예외 → HTTP 매핑:
 * - {@link IllegalArgumentException}, validation 실패, 파라미터 파싱 실패 → 400
 * - {@link IllegalStateException} → 409
 * - 업로드 크기 초과 → 413
 * - 나머지 {@link Exception} → 500 (메시지는 내부 디버깅용 로그에만 기록, 외부에는 일반 메시지)
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException ex, HandlerMethod handlerMethod,
                                        HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.warn("IllegalArgumentException: {} (uri={})", ex.getMessage(), request.getRequestURI());
        return respond(handlerMethod, request, redirectAttributes,
                HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public Object handleIllegalState(IllegalStateException ex, HandlerMethod handlerMethod,
                                     HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.warn("IllegalStateException: {} (uri={})", ex.getMessage(), request.getRequestURI());
        return respond(handlerMethod, request, redirectAttributes,
                HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, NumberFormatException.class})
    public Object handleBadRequest(Exception ex, HandlerMethod handlerMethod,
                                   HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.warn("요청 파라미터 오류: {} (uri={})", ex.getMessage(), request.getRequestURI());
        return respond(handlerMethod, request, redirectAttributes,
                HttpStatus.BAD_REQUEST, "BAD_REQUEST", "잘못된 요청 파라미터입니다.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleUploadTooLarge(MaxUploadSizeExceededException ex, HandlerMethod handlerMethod,
                                       HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.warn("업로드 크기 초과: {} (uri={})", ex.getMessage(), request.getRequestURI());
        return respond(handlerMethod, request, redirectAttributes,
                HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "업로드 파일 크기가 제한을 초과했습니다.");
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HandlerMethod handlerMethod,
                                   HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.error("미처리 예외: uri={}", request.getRequestURI(), ex);
        return respond(handlerMethod, request, redirectAttributes,
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }

    /** 핸들러 종류에 따라 JSON(ResponseEntity) 또는 redirect 뷰 이름을 반환. */
    private Object respond(HandlerMethod handlerMethod, HttpServletRequest request,
                           RedirectAttributes redirectAttributes,
                           HttpStatus status, String code, String message) {
        if (isJsonHandler(handlerMethod)) {
            return ResponseEntity.status(status).body(ErrorResponse.of(code, message));
        }
        redirectAttributes.addFlashAttribute("errorMessage", message);
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            return "redirect:" + referer;
        }
        // referer가 없으면 공용 에러 페이지로 폴백 (직접 URL 접근 등)
        request.setAttribute("errorMessage", message);
        request.setAttribute("errorStatus", status.value());
        return "error";
    }

    /** @ResponseBody 메서드 또는 @RestController 클래스이면 JSON 응답. */
    private boolean isJsonHandler(HandlerMethod handlerMethod) {
        if (handlerMethod == null) return false;
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), ResponseBody.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), ResponseBody.class);
    }
}