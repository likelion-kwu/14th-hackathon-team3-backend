package com.example.likelionhackathon.domain.user.controller;

import com.example.likelionhackathon.domain.auth.entity.EmailVerification;
import com.example.likelionhackathon.domain.auth.repository.EmailVerificationRepository;
import com.example.likelionhackathon.domain.user.entity.User;
import com.example.likelionhackathon.domain.user.entity.UserEnums.ActivityStatus;
import com.example.likelionhackathon.domain.user.repository.UserRepository;
import com.example.likelionhackathon.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailVerificationRepository verificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        verificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void signupStoresUserAfterEmailVerification() throws Exception {
        saveVerifiedEmail("user@example.com");

        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("user@example.com", "password123!", "password123!")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("201CREATED"))
                .andExpect(jsonPath("$.data.userId").isNumber());

        User saved = userRepository.findByEmail("user@example.com").orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("password123!");
        assertThat(passwordEncoder.matches("password123!", saved.getPassword())).isTrue();
        assertThat(saved.getCountry()).isNull();
        assertThat(saved.getLanguage()).isNull();
        assertThat(saved.getTimezone()).isNull();
        assertThat(saved.getActivityStatus()).isEqualTo(ActivityStatus.OFF);
        assertThat(verificationRepository.findByEmail("user@example.com")).isEmpty();
    }

    @Test
    void signupRejectsMissingOrUnverifiedEmail() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("missing@example.com", "password123!", "password123!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400EMAIL_NOT_VERIFIED"));

        verificationRepository.save(EmailVerification.create("unverified@example.com",
                passwordEncoder.encode("123456"), OffsetDateTime.now().plusMinutes(5),
                OffsetDateTime.now().minusSeconds(1)));
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("unverified@example.com", "password123!", "password123!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400EMAIL_NOT_VERIFIED"));
    }

    @Test
    void signupRejectsPasswordMismatch() throws Exception {
        saveVerifiedEmail("user@example.com");
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("user@example.com", "password123!", "differentPassword!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400PASSWORD_MISMATCH"));
    }

    @Test
    void signupChecksPasswordMismatchBeforeEmailVerification() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("unverified@example.com", "password123!", "differentPassword!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400PASSWORD_MISMATCH"));
    }

    @Test
    void signupRejectsEmailVerificationOlderThanThirtyMinutes() throws Exception {
        EmailVerification verification = EmailVerification.create("expired@example.com",
                passwordEncoder.encode("123456"), OffsetDateTime.now().plusMinutes(5),
                OffsetDateTime.now().minusSeconds(1));
        verification.verify(OffsetDateTime.now().minusMinutes(31));
        verificationRepository.save(verification);

        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("expired@example.com", "password123!", "password123!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400EMAIL_NOT_VERIFIED"));
    }

    @Test
    void signupRejectsPasswordOverSeventyTwoUtf8Bytes() throws Exception {
        String password = "a".repeat(73);
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("user@example.com", password, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));

        String multibytePassword = "가".repeat(25);
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("user@example.com", multibytePassword, multibytePassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));
    }

    @Test
    void signupAcceptsPasswordOfExactlySeventyTwoUtf8Bytes() throws Exception {
        saveVerifiedEmail("boundary@example.com");
        String password = "a".repeat(72);

        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("boundary@example.com", password, password)))
                .andExpect(status().isCreated());

        assertThat(passwordEncoder.matches(password, userRepository.findByEmail("boundary@example.com")
                .orElseThrow().getPassword())).isTrue();
    }

    @Test
    void signupRejectsDuplicateEmail() throws Exception {
        userRepository.save(User.create("duplicate@example.com", passwordEncoder.encode("password123!"), "홍길동"));
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("duplicate@example.com", "password123!", "password123!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409DUPLICATE_EMAIL"));
    }

    @Test
    void signupRejectsInvalidEmailAndBlankPasswordConfirm() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("invalid-email", "password123!", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));
    }

    @Test
    void signupUserCanLoginWithSameCredentials() throws Exception {
        saveVerifiedEmail("login@example.com");
        mockMvc.perform(post("/api/v1/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest("login@example.com", "password123!", "password123!")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login@example.com\",\"password\":\"password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200OK"))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        assertThat(userRepository.findByEmail("login@example.com").orElseThrow().getActivityStatus())
                .isEqualTo(ActivityStatus.OFF);
    }

    @Test
    void getActivityStatusReturnsOffAndActive() throws Exception {
        User user = saveUser("status@example.com");

        mockMvc.perform(get(activityStatusUrl()).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200OK"))
                .andExpect(jsonPath("$.data.status").value("OFF"));

        user.changeActivityStatus(ActivityStatus.ACTIVE);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get(activityStatusUrl()).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void updateActivityStatusSupportsActiveAndOff() throws Exception {
        User user = saveUser("update-status@example.com");

        mockMvc.perform(patch(activityStatusUrl())
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getActivityStatus())
                .isEqualTo(ActivityStatus.ACTIVE);

        mockMvc.perform(patch(activityStatusUrl())
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getActivityStatus())
                .isEqualTo(ActivityStatus.OFF);
    }

    @Test
    void updateActivityStatusChangesOnlyJwtUser() throws Exception {
        User currentUser = saveUser("current@example.com");
        User otherUser = saveUser("other@example.com");

        mockMvc.perform(patch(activityStatusUrl())
                        .header("Authorization", bearer(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(currentUser.getId()).orElseThrow().getActivityStatus())
                .isEqualTo(ActivityStatus.ACTIVE);
        assertThat(userRepository.findById(otherUser.getId()).orElseThrow().getActivityStatus())
                .isEqualTo(ActivityStatus.OFF);
    }

    @Test
    void updateActivityStatusRejectsInvalidStatus() throws Exception {
        User user = saveUser("invalid-status@example.com");

        for (String body : List.of(
                "{}",
                "{\"status\":null}",
                "{\"status\":\"\"}",
                "{\"status\":\"BUSY\"}"
        )) {
            mockMvc.perform(patch(activityStatusUrl())
                            .header("Authorization", bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));
        }
    }

    @Test
    void activityStatusApisRequireJwt() throws Exception {
        mockMvc.perform(get(activityStatusUrl()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401UNAUTHORIZED"));

        mockMvc.perform(patch(activityStatusUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401UNAUTHORIZED"));
    }

    @Test
    void activityStatusReturnsUserNotFoundForMissingJwtUser() throws Exception {
        String authorization = "Bearer " + jwtTokenProvider.createAccessToken(Long.MAX_VALUE);

        mockMvc.perform(get(activityStatusUrl()).header("Authorization", authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404USER_NOT_FOUND"));
    }

    @Test
    void getLanguageReturnsSupportedLanguages() throws Exception {
        User user = saveUser("language@example.com");

        for (String language : List.of("ko", "en", "ja")) {
            user.changeLanguage(language);
            userRepository.saveAndFlush(user);

            mockMvc.perform(get(languageUrl()).header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200OK"))
                    .andExpect(jsonPath("$.message").value("기본 언어를 조회했습니다."))
                    .andExpect(jsonPath("$.data.language").value(language));
        }
    }

    @Test
    void getLanguageAllowsNullForExistingUser() throws Exception {
        User user = saveUser("language-null@example.com");

        mockMvc.perform(get(languageUrl()).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.language").value(nullValue()));
    }

    @Test
    void changeLanguageSupportsKoEnAndJa() throws Exception {
        User user = saveUser("change-language@example.com");

        for (String language : List.of("ko", "en", "ja")) {
            mockMvc.perform(patch(languageUrl())
                            .header("Authorization", bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"language\":\"" + language + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200OK"))
                    .andExpect(jsonPath("$.message").value("기본 언어가 변경되었습니다."))
                    .andExpect(jsonPath("$.data.language").value(language));

            assertThat(userRepository.findById(user.getId()).orElseThrow().getLanguage())
                    .isEqualTo(language);
        }
    }

    @Test
    void changeLanguageReplacesExistingLanguage() throws Exception {
        User user = saveUser("replace-language@example.com");
        user.changeLanguage("ko");
        userRepository.saveAndFlush(user);

        mockMvc.perform(patch(languageUrl())
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.language").value("en"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getLanguage()).isEqualTo("en");
    }

    @Test
    void changeLanguageRejectsMissingBlankAndUnsupportedValues() throws Exception {
        User user = saveUser("invalid-language@example.com");

        for (String body : List.of(
                "{}",
                "{\"language\":null}",
                "{\"language\":\"\"}",
                "{\"language\":\"   \"}",
                "{\"language\":\"KO\"}",
                "{\"language\":\"EN\"}",
                "{\"language\":\"JA\"}",
                "{\"language\":\"kr\"}",
                "{\"language\":\"jp\"}",
                "{\"language\":\"english\"}",
                "{\"language\":\"unsupported\"}"
        )) {
            mockMvc.perform(patch(languageUrl())
                            .header("Authorization", bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));
        }
    }

    @Test
    void languageApisRequireJwt() throws Exception {
        mockMvc.perform(get(languageUrl()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401UNAUTHORIZED"));

        mockMvc.perform(patch(languageUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"ko\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401UNAUTHORIZED"));
    }

    @Test
    void languageApisReturnUserNotFoundForMissingJwtUser() throws Exception {
        String authorization = "Bearer " + jwtTokenProvider.createAccessToken(Long.MAX_VALUE);

        mockMvc.perform(get(languageUrl()).header("Authorization", authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404USER_NOT_FOUND"));

        mockMvc.perform(patch(languageUrl())
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"ko\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404USER_NOT_FOUND"));
    }

    @Test
    void regionApisStoreIanaTimezoneAndReturnRegion() throws Exception {
        User user = saveUser("region@example.com");
        String[][] cases = {{"SEOUL", "Asia/Seoul"}, {"TOKYO", "Asia/Tokyo"},
                {"NEW_YORK", "America/New_York"}, {"LOS_ANGELES", "America/Los_Angeles"}};
        for (String[] value : cases) {
            mockMvc.perform(patch(regionUrl()).header("Authorization", bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"region\":\"" + value[0] + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.region").value(value[0]))
                    .andExpect(jsonPath("$.data.timezone").value(value[1]));
            assertThat(userRepository.findById(user.getId()).orElseThrow().getTimezone()).isEqualTo(value[1]);
            mockMvc.perform(get(regionUrl()).header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.region").value(value[0]))
                    .andExpect(jsonPath("$.data.timezone").value(value[1]));
        }
    }

    @Test
    void getRegionSafelyReturnsUnsetAndPatchRejectsUnsupportedValue() throws Exception {
        User user = saveUser("region-unset@example.com");
        mockMvc.perform(get(regionUrl()).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.region").value(nullValue()))
                .andExpect(jsonPath("$.data.timezone").value(nullValue()));
        mockMvc.perform(patch(regionUrl()).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"LONDON\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400INVALID_INPUT_VALUE"));
    }

    private void saveVerifiedEmail(String email) {
        EmailVerification verification = EmailVerification.create(email,
                passwordEncoder.encode("123456"), OffsetDateTime.now().plusMinutes(5),
                OffsetDateTime.now().minusSeconds(1));
        verification.verify(OffsetDateTime.now());
        verificationRepository.save(verification);
    }

    private String signupRequest(String email, String password, String passwordConfirm) {
        return """
                {"name":"홍길동","email":"%s","password":"%s","passwordConfirm":"%s"}
                """.formatted(email, password, passwordConfirm);
    }

    private User saveUser(String email) {
        return userRepository.save(User.create(email, passwordEncoder.encode("password123!"), "User"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }

    private String activityStatusUrl() {
        return "/api/v1/users/me/activity-status";
    }

    private String languageUrl() {
        return "/api/v1/users/me/language";
    }

    private String regionUrl() { return "/api/v1/users/me/region"; }
}
