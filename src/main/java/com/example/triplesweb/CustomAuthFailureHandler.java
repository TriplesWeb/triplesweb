package com.example.triplesweb;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        org.springframework.security.core.AuthenticationException exception)
            throws IOException, ServletException {

        String errorMessage;

        if (exception instanceof UsernameNotFoundException ||
                exception instanceof InternalAuthenticationServiceException) {
            errorMessage = "존재하지 않는 아이디입니다.";
        } else if (exception instanceof BadCredentialsException) {
            errorMessage = "비밀번호가 틀렸습니다.";
        } else {
            errorMessage = "로그인 실패. 관리자에게 문의하세요.";
        }
        response.sendRedirect("/login?error=true&message=" + java.net.URLEncoder.encode(errorMessage, "UTF-8"));
    }
}
