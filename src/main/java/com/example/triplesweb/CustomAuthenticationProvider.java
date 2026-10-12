package com.example.triplesweb;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class CustomAuthenticationProvider implements AuthenticationProvider {
    private final CustomUserDetailsService userDetailService;
    private final PasswordEncoder passwordEncoder;

    public CustomAuthenticationProvider(CustomUserDetailsService userDetailsService,
                                        PasswordEncoder passwordEncoder) {
        this.userDetailService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String userid = authentication.getName();
        String rawPassword = authentication.getCredentials().toString();

        UserDetails user;
        try {
            user = userDetailService.loadUserByUsername(userid);
        } catch (UsernameNotFoundException ex) {
            // 아이디 없으면 여기서 예외 던져서 실패 핸들러로 전달
            throw ex;
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadCredentialsException("비밀번호가 틀렸습니다.");
        }

        // 인증 성공 시 토큰 리턴
        return new UsernamePasswordAuthenticationToken(user, rawPassword, user.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        // UsernamePasswordAuthenticationToken 타입의 인증을 처리
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
