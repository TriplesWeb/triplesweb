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
                        .requestMatchers(
                                "/", "/main", "/login", "/signup",
                                "/css/**", "/js/**",
                                "/h2-console/**","/api/**","/uploads/**",
                                "/images/**","/media/**", "/css/**",
                                "/js/**"
                        ).permitAll()
                        // ✅ 게시판/미디어/샵: GET은 모두 열람 가능 (목록/상세 뷰)
                        .requestMatchers(HttpMethod.GET,
                                "/article/**","/artist/**",
                                "/album/**",
                                "/popular/**",
                                "/triples/**",
                                "/objekt/**","/archive/**","/schedule/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,   "/article/**").authenticated()
                        .requestMatchers(HttpMethod.PUT,    "/article/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH,  "/article/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/article/**").authenticated()
                        .requestMatchers("/admin/**", "/media_new/**","/media_delete/**",
                                "/shop/shop_new/**","/notice_delete/**","/shop/uploadFile").hasRole("ADMIN")
                        .anyRequest().authenticated() // ✅ 안전하게 권한 확인
                )
                .formLogin (login -> login
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
                .logoutSuccessUrl("/main")
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

            response.sendRedirect("/main");
        };
    }
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/static/**", "/css/**", "/js/**", "/uploads/**");
    }
}
