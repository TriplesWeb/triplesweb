package com.example.triplesweb;

import com.example.triplesweb.entity.Members;
import com.example.triplesweb.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private MemberRepository memberRepository;


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Members member = memberRepository.findByUserId(username)
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 아이디입니다."));

        return org.springframework.security.core.userdetails.User.builder()
                .username(member.getUserId())
                .password(member.getPassword())
                .authorities(member.getRole())
                .build();
    }
}
