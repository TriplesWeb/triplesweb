package com.example.triplesweb.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

/**
 * About 트리플에스 페이지에서 "데이터 없음"을 404로 매핑한다.
 * REST API 컨트롤러의 IllegalArgumentException 처리는 각자 유지한다.
 */
@ControllerAdvice(assignableTypes = com.example.triplesweb.controller.TriplesController.class)
public class TriplesMvcExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView notFound(IllegalArgumentException ex) {
        ModelAndView mav = new ModelAndView("triples/not-found");
        mav.setStatus(HttpStatus.NOT_FOUND);
        String message = ex.getMessage() != null ? ex.getMessage() : "요청한 페이지를 찾을 수 없습니다.";
        mav.addObject("message", message);
        mav.addObject("listPath", guessListPath(message));
        mav.addObject("listLabel", guessListLabel(message));
        return mav;
    }

    private static String guessListPath(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("앨범") || lower.contains("album")) {
            return "/triples/album";
        }
        if (lower.contains("유닛") || lower.contains("unit")) {
            return "/triples/unit";
        }
        if (lower.contains("아티스트") || lower.contains("멤버") || lower.contains("artist")) {
            return "/triples/artist";
        }
        return "/triples/artist";
    }

    private static String guessListLabel(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("앨범") || lower.contains("album")) {
            return "앨범 목록으로";
        }
        if (lower.contains("유닛") || lower.contains("unit")) {
            return "유닛 목록으로";
        }
        return "그룹 소개로";
    }
}
