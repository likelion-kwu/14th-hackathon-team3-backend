package com.example.likelionhackathon.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "400INVALID_INPUT_VALUE", "올바르지 않은 입력값입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "404RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "405METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),

    // User / Auth
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "404USER_NOT_FOUND", "존재하지 않는 사용자입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "409DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다."),
    INVALID_LOGIN_CREDENTIALS(HttpStatus.UNAUTHORIZED, "401INVALID_LOGIN_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    EMAIL_VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "404EMAIL_VERIFICATION_NOT_FOUND", "이메일 인증 요청을 찾을 수 없습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "400INVALID_VERIFICATION_CODE", "인증번호가 올바르지 않습니다."),
    EXPIRED_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "400EXPIRED_VERIFICATION_CODE", "인증번호가 만료되었습니다."),
    VERIFICATION_REQUEST_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "429VERIFICATION_REQUEST_TOO_FREQUENT", "인증번호는 60초 후에 다시 요청할 수 있습니다."),
    VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.BAD_REQUEST, "400VERIFICATION_ATTEMPTS_EXCEEDED", "인증번호 입력 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "500EMAIL_SEND_FAILED", "인증 이메일 발송에 실패했습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "400EMAIL_NOT_VERIFIED", "이메일 인증이 필요합니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "400PASSWORD_MISMATCH", "비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    PASSWORD_RESET_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "400PASSWORD_RESET_NOT_VERIFIED", "비밀번호 재설정 인증이 필요합니다."),
    INVALID_PASSWORD_RESET_TOKEN(HttpStatus.BAD_REQUEST, "400INVALID_PASSWORD_RESET_TOKEN", "비밀번호 재설정 토큰이 올바르지 않습니다."),
    EXPIRED_PASSWORD_RESET_TOKEN(HttpStatus.BAD_REQUEST, "400EXPIRED_PASSWORD_RESET_TOKEN", "비밀번호 재설정 토큰이 만료되었습니다."),
    UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "401UNAUTHORIZED", "인증 정보가 유효하지 않습니다."),

    // Workspace
    INVALID_WORKSPACE_INPUT(HttpStatus.BAD_REQUEST, "400INVALID_WORKSPACE_INPUT", "워크스페이스 입력값이 올바르지 않습니다."),
    WORKSPACE_NAME_DUPLICATED(HttpStatus.CONFLICT, "409WORKSPACE_NAME_DUPLICATED", "동일한 워크스페이스 이름이 존재합니다."),
    WORKSPACE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "403WORKSPACE_ACCESS_DENIED", "워크스페이스 접근 권한이 없습니다."),
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "404WORKSPACE_NOT_FOUND", "워크스페이스를 찾을 수 없습니다."),
    WORKSPACE_ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "403WORKSPACE_ADMIN_REQUIRED", "워크스페이스 관리 권한이 필요합니다."),
    WORKSPACE_VERSION_CONFLICT(HttpStatus.CONFLICT, "409WORKSPACE_VERSION_CONFLICT", "다른 사용자가 워크스페이스를 먼저 수정했습니다."),
    INVALID_INVITATION_INPUT(HttpStatus.BAD_REQUEST, "400INVALID_INVITATION_INPUT", "초대 방식 또는 이메일이 올바르지 않습니다."),
    ALREADY_WORKSPACE_MEMBER(HttpStatus.CONFLICT, "409ALREADY_WORKSPACE_MEMBER", "이미 워크스페이스 멤버입니다."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "404INVITATION_NOT_FOUND", "초대 정보를 찾을 수 없습니다."),
    INVITATION_EXPIRED(HttpStatus.GONE, "410INVITATION_EXPIRED", "초대가 만료되었습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "404MEMBER_NOT_FOUND", "멤버를 찾을 수 없습니다."),
    LAST_OWNER_CANNOT_CHANGE(HttpStatus.CONFLICT, "409LAST_OWNER_CANNOT_CHANGE", "마지막 OWNER는 변경할 수 없습니다."),

    // Conversation
    SELF_CONVERSATION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "400SELF_CONVERSATION_NOT_ALLOWED", "자기 자신과의 대화는 생성할 수 없습니다."),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "404CONVERSATION_NOT_FOUND", "대화를 찾을 수 없습니다."),
    CONVERSATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "403CONVERSATION_ACCESS_DENIED", "대화 접근 권한이 없습니다."),
    TRANSLATION_LANGUAGE_NOT_CONFIGURED(HttpStatus.UNPROCESSABLE_CONTENT, "422TRANSLATION_LANGUAGE_NOT_CONFIGURED", "메시지 번역에 필요한 사용자 언어가 설정되지 않았습니다."),
    TEMPORAL_CONTEXT_NOT_CONFIGURED(HttpStatus.UNPROCESSABLE_CONTENT, "422TEMPORAL_CONTEXT_NOT_CONFIGURED", "날짜와 시간을 명확화하는 데 필요한 사용자 시간대가 설정되지 않았습니다."),
    AI_TRANSLATION_FAILED(HttpStatus.BAD_GATEWAY, "502AI_TRANSLATION_FAILED", "AI 메시지 번역에 실패했습니다."),
    AI_TRANSLATION_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "504AI_TRANSLATION_TIMEOUT", "AI 메시지 번역 요청 시간이 초과되었습니다."),

    // Project
    INVALID_PROJECT_INPUT(HttpStatus.BAD_REQUEST, "400INVALID_PROJECT_INPUT", "프로젝트 입력값이 올바르지 않습니다."),
    PROJECT_CREATE_DENIED(HttpStatus.FORBIDDEN, "403PROJECT_CREATE_DENIED", "프로젝트 생성 권한이 없습니다."),
    PROJECT_NAME_DUPLICATED(HttpStatus.CONFLICT, "409PROJECT_NAME_DUPLICATED", "동일한 프로젝트 이름이 존재합니다."),
    PROJECT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "403PROJECT_ACCESS_DENIED", "프로젝트 접근 권한이 없습니다."),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "404PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다."),
    PROJECT_ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "403PROJECT_ADMIN_REQUIRED", "프로젝트 관리 권한이 필요합니다."),
    PROJECT_VERSION_CONFLICT(HttpStatus.CONFLICT, "409PROJECT_VERSION_CONFLICT", "다른 사용자가 프로젝트를 먼저 수정했습니다."),
    INVALID_TEAM_SETTING(HttpStatus.BAD_REQUEST, "400INVALID_TEAM_SETTING", "팀 설정이 올바르지 않습니다."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "404TEAM_NOT_FOUND", "팀을 찾을 수 없습니다."),
    INVALID_TIMEZONE(HttpStatus.UNPROCESSABLE_CONTENT, "422INVALID_TIMEZONE", "유효하지 않은 타임존입니다."),
    INVALID_MEMBER_ACTION(HttpStatus.BAD_REQUEST, "400INVALID_MEMBER_ACTION", "멤버 관리 작업이 올바르지 않습니다."),
    ALREADY_PROJECT_MEMBER(HttpStatus.CONFLICT, "409ALREADY_PROJECT_MEMBER", "이미 프로젝트 멤버입니다."),
    MEMBER_OR_TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "404MEMBER_OR_TEAM_NOT_FOUND", "멤버 또는 팀을 찾을 수 없습니다."),
    LAST_PROJECT_ADMIN_CANNOT_CHANGE(HttpStatus.CONFLICT, "409LAST_PROJECT_ADMIN_CANNOT_CHANGE", "마지막 프로젝트 관리자는 변경할 수 없습니다."),
    INVALID_INTEGRATION_ACTION(HttpStatus.BAD_REQUEST, "400INVALID_INTEGRATION_ACTION", "외부 연동 작업이 올바르지 않습니다."),
    INTEGRATION_NOT_FOUND(HttpStatus.NOT_FOUND, "404INTEGRATION_NOT_FOUND", "외부 연동을 찾을 수 없습니다."),
    OAUTH_SCOPE_INSUFFICIENT(HttpStatus.UNPROCESSABLE_CONTENT, "422OAUTH_SCOPE_INSUFFICIENT", "외부 연동 권한 범위가 부족합니다."),
    OAUTH_STATE_INVALID(HttpStatus.BAD_REQUEST, "400OAUTH_STATE_INVALID", "OAuth 인증 상태가 만료되었거나 올바르지 않습니다."),
    OAUTH_CONFIGURATION_MISSING(HttpStatus.SERVICE_UNAVAILABLE, "503OAUTH_CONFIGURATION_MISSING", "OAuth 제공사 설정이 완료되지 않았습니다."),
    OAUTH_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "502OAUTH_PROVIDER_ERROR", "OAuth 제공사 인증에 실패했습니다."),
    OAUTH_TOKEN_ENCRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "500OAUTH_TOKEN_ENCRYPTION_FAILED", "OAuth 토큰 보호 처리에 실패했습니다."),

    // Handover
    INVALID_SOURCE_RANGE(HttpStatus.BAD_REQUEST, "400INVALID_SOURCE_RANGE", "수집 기간이 올바르지 않습니다."),
    PROJECT_OR_CYCLE_NOT_FOUND(HttpStatus.NOT_FOUND, "404PROJECT_OR_CYCLE_NOT_FOUND", "프로젝트 또는 Cycle을 찾을 수 없습니다."),
    HANDOVER_ALREADY_GENERATING(HttpStatus.CONFLICT, "409HANDOVER_ALREADY_GENERATING", "인수인계 생성 작업이 이미 진행 중입니다."),
    NO_CONNECTED_SOURCE(HttpStatus.UNPROCESSABLE_CONTENT, "422NO_CONNECTED_SOURCE", "연결된 협업 도구가 없습니다."),
    HANDOVER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "403HANDOVER_ACCESS_DENIED", "인수인계 접근 권한이 없습니다."),
    HANDOVER_NOT_FOUND(HttpStatus.NOT_FOUND, "404HANDOVER_NOT_FOUND", "인수인계를 찾을 수 없습니다."),
    HANDOVER_LOCKED(HttpStatus.CONFLICT, "409HANDOVER_LOCKED", "예약 또는 전달 완료된 인수인계입니다."),
    REFRESH_ALREADY_RUNNING(HttpStatus.CONFLICT, "409REFRESH_ALREADY_RUNNING", "최신 활동 갱신 작업이 이미 진행 중입니다."),
    SOURCE_SYNC_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "422SOURCE_SYNC_FAILED", "협업 도구 동기화에 실패했습니다."),
    INVALID_DRAFT(HttpStatus.BAD_REQUEST, "400INVALID_DRAFT", "인수인계 항목 또는 전달 정보가 올바르지 않습니다."),
    DRAFT_VERSION_CONFLICT(HttpStatus.CONFLICT, "409DRAFT_VERSION_CONFLICT", "다른 사용자가 인수인계 초안을 먼저 저장했습니다."),
    INVALID_EVIDENCE(HttpStatus.UNPROCESSABLE_CONTENT, "422INVALID_EVIDENCE", "유효하지 않은 근거가 포함되어 있습니다."),
    HANDOVER_NOT_READY(HttpStatus.CONFLICT, "409HANDOVER_NOT_READY", "인수인계 초안 또는 전달 설정이 완료되지 않았습니다."),
    HANDOVER_VERSION_CONFLICT(HttpStatus.CONFLICT, "409HANDOVER_VERSION_CONFLICT", "최신 인수인계 초안 버전이 아닙니다."),
    HANDOVER_ALREADY_DELIVERED(HttpStatus.CONFLICT, "409HANDOVER_ALREADY_DELIVERED", "인수인계가 이미 전달 또는 예약되었습니다."),
    REVIEW_ALERT_NOT_ACKNOWLEDGED(HttpStatus.UNPROCESSABLE_CONTENT, "422REVIEW_ALERT_NOT_ACKNOWLEDGED", "확인 필요 항목을 인지해야 합니다."),

    // Cycle
    // 사이클 명세의 공통 규약은 실패 code를 "상태코드 + 도메인" 형식으로 정의한다. (ex. 404CYCLE)
    // 상세 사유는 CustomException 의 detailMessage 로 구분한다.
    CYCLE_INVALID_INPUT(HttpStatus.BAD_REQUEST, "400CYCLE", "사이클 입력값이 올바르지 않습니다."),
    CYCLE_NOT_FOUND(HttpStatus.NOT_FOUND, "404CYCLE", "존재하지 않는 사이클입니다."),
    CYCLE_CONFLICT(HttpStatus.CONFLICT, "409CYCLE", "요청을 처리할 수 없는 사이클 상태입니다."),

    // Issue
    ISSUE_INVALID_INPUT(HttpStatus.BAD_REQUEST, "400ISSUE", "이슈 입력값이 올바르지 않습니다."),
    ISSUE_NOT_FOUND(HttpStatus.NOT_FOUND, "404ISSUE", "존재하지 않는 이슈입니다."),
    ISSUE_CONFLICT(HttpStatus.CONFLICT, "409ISSUE", "요청을 처리할 수 없는 이슈 상태입니다."),
    ISSUE_FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "413ISSUE", "파일 용량이 초과되었습니다."),

    COMMENT_INVALID_INPUT(HttpStatus.BAD_REQUEST, "400COMMENT", "댓글 입력값이 올바르지 않습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "404COMMENT", "존재하지 않는 댓글입니다."),
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "403COMMENT", "본인이 작성한 댓글만 수정하거나 삭제할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
