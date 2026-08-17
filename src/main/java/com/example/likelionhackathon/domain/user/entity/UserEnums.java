package com.example.likelionhackathon.domain.user.entity;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;

public final class UserEnums {

    private UserEnums() {
    }

    public enum ActivityStatus {
        ACTIVE,
        OFF
    }

    public enum UserRegion {
        SEOUL("Asia/Seoul"),
        TOKYO("Asia/Tokyo"),
        NEW_YORK("America/New_York"),
        LOS_ANGELES("America/Los_Angeles");

        private final String timezone;

        UserRegion(String timezone) {
            this.timezone = timezone;
        }

        public String getTimezone() {
            return timezone;
        }

        public ZoneId toZoneId() {
            return ZoneId.of(timezone);
        }

        public static Optional<UserRegion> fromTimezone(String timezone) {
            if (timezone == null) return Optional.empty();
            return Arrays.stream(values()).filter(region -> region.timezone.equals(timezone)).findFirst();
        }
    }
}
