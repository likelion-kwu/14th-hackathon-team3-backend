package com.example.likelionhackathon.domain.user.entity;

import com.example.likelionhackathon.domain.user.entity.UserEnums.ActivityStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_status", nullable = false, length = 16)
    private ActivityStatus activityStatus;

    public static User create(
            String email,
            String encodedPassword,
            String name
    ) {
        User user = new User();
        user.email = email;
        user.password = encodedPassword;
        user.name = name;
        user.activityStatus = ActivityStatus.OFF;
        return user;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void changeActivityStatus(ActivityStatus activityStatus) {
        this.activityStatus = activityStatus;
    }

    public void changeLanguage(String language) {
        this.language = language;
    }

    public void changeTimezone(String timezone) {
        this.timezone = timezone;
    }
}
