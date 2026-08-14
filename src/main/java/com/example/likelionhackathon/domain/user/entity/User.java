package com.example.likelionhackathon.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(length = 2)
    private String country;

    @Column(length = 10)
    private String language;

    @Column(length = 50)
    private String timezone;

    public static User create(
            String email,
            String encodedPassword,
            String name
    ) {
        User user = new User();
        user.email = email;
        user.password = encodedPassword;
        user.name = name;
        return user;
    }
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
