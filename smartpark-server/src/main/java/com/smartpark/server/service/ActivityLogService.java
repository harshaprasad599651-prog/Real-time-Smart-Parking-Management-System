package com.smartpark.server.service;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.ActivityLog;
import com.smartpark.server.document.User;
import com.smartpark.server.document.UserRole;
import com.smartpark.server.dto.ActivityLogDto;
import com.smartpark.server.event.ParkingDomainEvent;
import com.smartpark.server.repository.ActivityLogRepository;
import com.smartpark.server.repository.UserRepository;
import com.smartpark.server.util.Normalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Records what users do, for the admin's activity monitor.
 * <ul>
 *     <li>Logging is best effort: a failure to write a log entry never breaks the business
 *         operation that triggered it.</li>
 *     <li>Every new entry is also announced (domain event {@code ActivityRecorded}) and pushed
 *         live to connected <b>admin</b> terminals only.</li>
 * </ul>
 */
@Service
public class ActivityLogService {

    public static final String SYSTEM_USER = "SYSTEM";
    private static final int MAX_PAGE = 500;
    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);

    private final ActivityLogRepository repository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public ActivityLogService(ActivityLogRepository repository, UserRepository userRepository,
                              MongoTemplate mongoTemplate, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
    }

    public void record(String username, ActivityAction action, String description) {
        try {
            // Usernames of failed sign-ins and descriptions may contain text typed by a client:
            // strip line breaks and control characters so log entries cannot be forged.
            ActivityLog saved = repository.save(new ActivityLog(Normalizer.label(username, 60, "?"), action,
                    Normalizer.freeText(description, 500), Instant.now()));
            eventPublisher.publishEvent(new ParkingDomainEvent.ActivityRecorded(ActivityLogDto.from(saved), username));
        } catch (RuntimeException e) {
            log.warn("Could not write activity log [{} {} {}]: {}", username, action, description, e.getMessage());
        }
    }

    public List<ActivityLogDto> recent(int limit) {
        return search(null, null, null, limit);
    }

    public List<ActivityLogDto> recentForUser(String username, int limit) {
        return search(username, null, null, limit);
    }

    /**
     * Newest first. All filters are optional and can be combined.
     *
     * @param role only actions by users who currently have this role (e.g. all entry operators)
     */
    public List<ActivityLogDto> search(String username, UserRole role, ActivityAction action, int limit) {
        Criteria c = new Criteria();
        if (username != null && !username.isBlank()) {
            c.and(ActivityLog.F_USERNAME).is(Normalizer.username(username));
        }
        if (role != null) {
            List<String> names = userRepository.findByRoleOrderByUsernameAsc(role).stream().map(User::getUsername).toList();
            if (username != null && !username.isBlank()) {
                if (!names.contains(Normalizer.username(username))) {
                    return List.of();
                }
            } else {
                c.and(ActivityLog.F_USERNAME).in(names);
            }
        }
        if (action != null) {
            c.and("action").is(action);
        }
        Query q = new Query(c).with(PageRequest.of(0, Math.clamp(limit, 1, MAX_PAGE),
                Sort.by(Sort.Direction.DESC, ActivityLog.F_TIMESTAMP)));
        return mongoTemplate.find(q, ActivityLog.class).stream().map(ActivityLogDto::from).toList();
    }
}
