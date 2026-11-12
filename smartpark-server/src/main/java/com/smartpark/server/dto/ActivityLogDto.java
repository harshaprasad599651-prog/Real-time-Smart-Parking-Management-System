package com.smartpark.server.dto;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.ActivityLog;

import java.time.Instant;

public record ActivityLogDto(String id, String username, ActivityAction action, String description, Instant timestamp) {

    public static ActivityLogDto from(ActivityLog a) {
        return new ActivityLogDto(a.getId(), a.getUsername(), a.getAction(), a.getDescription(), a.getTimestamp());
    }
}
