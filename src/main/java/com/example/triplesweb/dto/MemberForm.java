package com.example.triplesweb.dto;

import com.example.triplesweb.entity.Members;
import lombok.*;
import jakarta.validation.constraints.*;

/** 회원가입 폼 바인딩. 검증 후 {@link com.example.triplesweb.entity.Members}로 변환. */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor

public class MemberForm {
    @Size(min = 7, message = "아이디는 최소 7자 이상이어야 합니다.")
    private String userId;

    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$",
            message = "비밀번호는 숫자와 영문을 포함해 8자 이상이어야 합니다."
    )
    private String password;
    
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    private String address;

    private String role = "ROLE_USER";

    private int level;

    private int points;

    public Members toEntity() {
        return new Members(null, userId, password, email, address, role, level, points);
    }

    @Data
    public static class DuplicateCheckResponse {
        private boolean userIdExists;
        private boolean emailExists;
    }
}
