package com.example.triplesweb.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Whitelabel 대신 사이트 공통 에러 화면을 렌더한다.
 */
@Controller
public class SiteErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object pathObj = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object messageObj = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object exceptionObj = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        int status = statusObj instanceof Integer ? (Integer) statusObj : 500;
        HttpStatus httpStatus = HttpStatus.resolve(status);
        String reason = httpStatus != null ? httpStatus.getReasonPhrase() : "Error";

        String path = pathObj != null ? pathObj.toString() : "";
        String detail = messageObj != null ? messageObj.toString().trim() : "";
        if (detail.isEmpty() && exceptionObj instanceof Throwable throwable) {
            String exceptionMessage = throwable.getMessage();
            if (exceptionMessage != null && !exceptionMessage.isBlank()) {
                detail = exceptionMessage.trim();
            }
        }

        model.addAttribute("status", status);
        model.addAttribute("error", reason);
        model.addAttribute("path", path);
        model.addAttribute("hasPath", !path.isBlank());
        model.addAttribute("detail", detail);
        model.addAttribute("hasDetail", !detail.isBlank());
        model.addAttribute("title", titleFor(status));
        model.addAttribute("description", descriptionFor(status));

        return "error";
    }

    private static String titleFor(int status) {
        return switch (status) {
            case 400 -> "잘못된 요청입니다";
            case 401 -> "로그인이 필요합니다";
            case 403 -> "접근 권한이 없습니다";
            case 404 -> "페이지를 찾을 수 없습니다";
            case 405 -> "허용되지 않은 요청입니다";
            case 408 -> "요청 시간이 초과되었습니다";
            case 429 -> "요청이 너무 많습니다";
            case 500 -> "서버 오류가 발생했습니다";
            case 502, 503, 504 -> "일시적으로 서비스를 이용할 수 없습니다";
            default -> "오류가 발생했습니다";
        };
    }

    private static String descriptionFor(int status) {
        return switch (status) {
            case 400 -> "요청 형식이 올바르지 않습니다. 입력값을 확인한 뒤 다시 시도해 주세요.";
            case 401 -> "이 페이지를 보려면 로그인이 필요합니다.";
            case 403 -> "이 페이지에 접근할 수 있는 권한이 없습니다.";
            case 404 -> "주소를 잘못 입력했거나, 페이지가 삭제·이동되었을 수 있습니다.";
            case 405 -> "이 주소에서는 해당 방식의 요청을 받을 수 없습니다.";
            case 408 -> "서버 응답이 늦어 요청이 취소되었습니다. 잠시 후 다시 시도해 주세요.";
            case 429 -> "짧은 시간에 요청이 몰렸습니다. 잠시 후 다시 시도해 주세요.";
            case 500 -> "처리 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.";
            case 502, 503, 504 -> "서버가 잠시 응답하지 않습니다. 잠시 후 다시 시도해 주세요.";
            default -> "요청을 처리하는 동안 문제가 발생했습니다.";
        };
    }
}
