package com.example.triplesweb;

import com.example.triplesweb.entity.Members;
import com.example.triplesweb.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security 설정: URL 권한, 폼 로그인·로그아웃, BCrypt, 로그인 성공 시 세션 회원 정보 저장.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {
    private final MemberRepository memberRepository;
    private final CustomAuthFailureHandler failureHandler;

    @Bean
    public UserDetailsService userDetailsService() {
        return (String username) -> {
            Members m = memberRepository.findByUserId(username)
                    .orElseThrow(() -> new UsernameNotFoundException("사용자 없음: " + username));
            // role 컬럼이 "ROLE_USER"/"ROLE_ADMIN" 형태라면 그대로 authority 로 넣습니다.
            return User.withUsername(m.getUserId())
                    .password(m.getPassword()) // 반드시 BCrypt 등으로 인코딩된 해시여야 함
                    .authorities(new SimpleGrantedAuthority(m.getRole()))
                    .build();
        };
    }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))

                .authorizeHttpRequests(auth -> auth
                        // AntPath: "/" 등 MVC 핸들러 유무와 무관하게 공개 경로를 확실히 허용
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/"),
                                AntPathRequestMatcher.antMatcher("/main"),
                                AntPathRequestMatcher.antMatcher("/login"),
                                AntPathRequestMatcher.antMatcher("/signup"),
                                AntPathRequestMatcher.antMatcher("/css/**"),
                                AntPathRequestMatcher.antMatcher("/js/**"),
                                AntPathRequestMatcher.antMatcher("/h2-console/**"),
                                AntPathRequestMatcher.antMatcher("/api/**"),
                                AntPathRequestMatcher.antMatcher("/uploads/**"),
                                AntPathRequestMatcher.antMatcher("/images/**"),
                                AntPathRequestMatcher.antMatcher("/media/**")
                        ).permitAll()
                        // ✅ 게시판/미디어/샵: GET은 모두 열람 가능 (목록/상세 뷰)
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/article/**"),
                                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/artist/**"),
                                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/album/**"),
                                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/popular/**"),
                                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/triples/**"),
                                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/objekt/**"),
                                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/archive/**"),
                                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/schedule/**")
                        ).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/article/**")).authenticated()
                        .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.PUT, "/article/**")).authenticated()
                        .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.PATCH, "/article/**")).authenticated()
                        .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.DELETE, "/article/**")).authenticated()
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/admin/**"),
                                AntPathRequestMatcher.antMatcher("/media_new/**"),
                                AntPathRequestMatcher.antMatcher("/media_delete/**"),
                                AntPathRequestMatcher.antMatcher("/shop/shop_new/**"),
                                AntPathRequestMatcher.antMatcher("/notice_delete/**"),
                                AntPathRequestMatcher.antMatcher("/shop/uploadFile")
                        ).hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(login -> login
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("userId")
                        .passwordParameter("password")
                        .successHandler(successHandler())
                        .failureHandler(failureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
        );
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
