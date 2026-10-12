package com.example.triplesweb.dto;

import com.example.triplesweb.entity.Members;
import lombok.*;

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
