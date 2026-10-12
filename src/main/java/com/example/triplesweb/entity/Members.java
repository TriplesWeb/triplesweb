package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor

public class Members {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String role = "ROLE_USER";

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private int points = 0;

    public Members(String userId, String password, String email, String address) {
        this.userId = userId;
        this.password = password;
        this.email = email;
        this.address = address;
        this.role = "ROLE_USER";
        this.level = 1;
        this.points = 0;
    }
    public Members(String userId, String password) {
        this.userId = userId;
        this.password = password;
        this.role = "ROLE_USER";
    }

    public void addPoints(int amount) {
        this.points += amount;
        checkLevelUp();
    }

    private void checkLevelUp() {
        if (points >= 300 && level < 10) {
            level = 10;
        } else if (points >= 100 && level < 5) {
            level = 5;
        }
    }
}
