package com.smartpark.server.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Append-only audit record of something a user (or the system) did.
 * Indexed by {@code timestamp} and by {@code (username, timestamp)}.
 */
@Document(collection = ActivityLog.COLLECTION)
public class ActivityLog {

    public static final String COLLECTION = "activityLogs";
    public static final String F_USERNAME = "username";
    public static final String F_TIMESTAMP = "timestamp";

    @Id
    private String id;
    private String username;
    private ActivityAction action;
    private String description;
    private Instant timestamp;

    public ActivityLog() {
    }

    public ActivityLog(String username, ActivityAction action, String description, Instant timestamp) {
        this.username = username;
        this.action = action;
        this.description = description;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public ActivityAction getAction() { return action; }
    public void setAction(ActivityAction action) { this.action = action; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
