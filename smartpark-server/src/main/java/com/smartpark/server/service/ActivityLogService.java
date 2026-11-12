package com.smartpark.server.service;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.ActivityLog;
import com.smartpark.server.dto.ActivityLogDto;
import com.smartpark.server.repository.ActivityLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Records what users do, for the admin's "operator activity" monitor.
 * <p>
 * Logging is best effort: a failure to write a log entry never breaks the business
 * operation that triggered it.
 */
@Service
public class ActivityLogService {

    public static final String SYSTEM_USER = "SYSTEM";
    private static final int MAX_PAGE = 500;
    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);

    private final ActivityLogRepository repository;

    public ActivityLogService(ActivityLogRepository repository) {
        this.repository = repository;
    }

    public void record(String username, ActivityAction action, String description) {
        try {
            repository.save(new ActivityLog(username, action, description, Instant.now()));
        } catch (RuntimeException e) {
            log.warn("Could not write activity log [{} {} {}]: {}", username, action, description, e.getMessage());
        }
    }

    public List<ActivityLogDto> recent(int limit) {
        return repository.findAllByOrderByTimestampDesc(page(limit)).stream().map(ActivityLogDto::from).toList();
    }

    public List<ActivityLogDto> recentForUser(String username, int limit) {
        return repository.findByUsernameOrderByTimestampDesc(username, page(limit)).stream()
                .map(ActivityLogDto::from).toList();
    }

    private static PageRequest page(int limit) {
        return PageRequest.of(0, Math.clamp(limit, 1, MAX_PAGE));
    }
}
