package com.example.triplesweb.dto;

import com.example.triplesweb.entity.Members;
import lombok.*;

/** 로그인 폼 바인딩. userId·password만 수신. */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor

public class LoginForm {
    private String userId;
    private String password;


    public Members toEntity() {
        return new Members(userId, password);
    }
}
