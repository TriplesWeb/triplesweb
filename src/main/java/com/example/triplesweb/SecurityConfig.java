package com.example.triplesweb;

import com.example.triplesweb.entity.Members;
import com.example.triplesweb.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Spring Security 설정.
 * 현재 로그인 기능 비활성화 — 모든 요청 허용, 폼 로그인·로그아웃 비활성.
 * (복구 시 formLogin/authorizeHttpRequests 규칙을 다시 활성화하면 됨)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {
    private final MemberRepository memberRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        return (String username) -> {
            Members m = memberRepository.findByUserId(username)
                    .orElseThrow(() -> new UsernameNotFoundException("사용자 없음: " + username));
            return User.withUsername(m.getUserId())
                    .password(m.getPassword())
                    .authorities(new SimpleGrantedAuthority(m.getRole()))
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(login -> login.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public AuthenticationSuccessHandler successHandler() {
        return (request, response, authentication) -> {
            String userId = authentication.getName();
            Members member = memberRepository.findByUserId(userId).orElse(null);

            if (member != null) {
                HttpSession session = request.getSession();
                session.setAttribute("loginMember", member);
                session.setAttribute("userId", member.getUserId());
                session.setAttribute("role", member.getRole());
            }

            response.sendRedirect("/");
        };
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/static/**", "/css/**", "/js/**", "/uploads/**");
    }
}
