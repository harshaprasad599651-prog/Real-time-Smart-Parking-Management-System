package com.smartpark.server.persistence;

import com.smartpark.server.document.ActivityLog;
import com.smartpark.server.document.LoginSession;
import com.smartpark.server.document.ParkingSession;
import com.smartpark.server.document.ParkingSlot;
import com.smartpark.server.document.SessionStatus;
import com.smartpark.server.document.User;
import com.smartpark.server.document.VehicleCategory;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.stereotype.Component;

/**
 * Creates all collections and indexes when the server starts, before any data
 * is read or written. Creating an index that already exists with the same
 * definition does nothing, so this is safe to run on every start.
 * <p>
 * The two <b>partial unique indexes</b> on {@code parkingSessions} are the core
 * database-level guarantees:
 * <ul>
 *     <li>{@code ux_sessions_active_vehicle}: unique {@code vehicleNumber} among ACTIVE sessions</li>
 *     <li>{@code ux_sessions_active_slot}: unique {@code parkingSlotCode} among ACTIVE sessions</li>
 * </ul>
 * COMPLETED sessions are not part of these indexes, so history can contain the same
 * vehicle or slot many times.
 */
@Component
public class MongoIndexInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    public MongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        ensureIndexes();
    }

    public void ensureIndexes() {
        // users
        ops(User.class).createIndex(new Index().on(User.F_USERNAME, Direction.ASC)
                .unique().named(IndexNames.USERS_USERNAME));

        // vehicleCategories
        ops(VehicleCategory.class).createIndex(new Index().on(VehicleCategory.F_CATEGORY_NAME, Direction.ASC)
                .unique().named(IndexNames.CATEGORIES_NAME));

        // parkingSlots
        IndexOperations slots = ops(ParkingSlot.class);
        slots.createIndex(new Index().on(ParkingSlot.F_SLOT_CODE, Direction.ASC)
                .unique().named(IndexNames.SLOTS_CODE));
        slots.createIndex(new Index()
                .on(ParkingSlot.F_FLOOR, Direction.ASC)
                .on(ParkingSlot.F_ROW, Direction.ASC)
                .on(ParkingSlot.F_COLUMN, Direction.ASC)
                .unique().named(IndexNames.SLOTS_POSITION));
        slots.createIndex(new Index()
                .on(ParkingSlot.F_STATUS, Direction.ASC)
                .on(ParkingSlot.F_SUPPORTED_CATEGORIES, Direction.ASC)
                .named(IndexNames.SLOTS_STATUS_CATEGORY));

        // parkingSessions
        IndexOperations sessions = ops(ParkingSession.class);
        PartialIndexFilter onlyActive = PartialIndexFilter.of(
                new Document(ParkingSession.F_STATUS, SessionStatus.ACTIVE.name()));
        sessions.createIndex(new Index().on(ParkingSession.F_TICKET_ID, Direction.ASC)
                .unique().named(IndexNames.SESSIONS_TICKET));
        sessions.createIndex(new Index().on(ParkingSession.F_VEHICLE_NUMBER, Direction.ASC)
                .unique().partial(onlyActive).named(IndexNames.SESSIONS_ACTIVE_VEHICLE));
        sessions.createIndex(new Index().on(ParkingSession.F_SLOT_CODE, Direction.ASC)
                .unique().partial(onlyActive).named(IndexNames.SESSIONS_ACTIVE_SLOT));
        sessions.createIndex(new Index()
                .on(ParkingSession.F_VEHICLE_NUMBER, Direction.ASC)
                .on(ParkingSession.F_ENTRY_TIME, Direction.DESC)
                .named(IndexNames.SESSIONS_VEHICLE_ENTRY));
        sessions.createIndex(new Index()
                .on(ParkingSession.F_STATUS, Direction.ASC)
                .on(ParkingSession.F_ENTRY_TIME, Direction.DESC)
                .named(IndexNames.SESSIONS_STATUS_ENTRY));

        // activityLogs
        IndexOperations activity = ops(ActivityLog.class);
        activity.createIndex(new Index().on(ActivityLog.F_TIMESTAMP, Direction.DESC)
                .named(IndexNames.ACTIVITY_TIMESTAMP));
        activity.createIndex(new Index()
                .on(ActivityLog.F_USERNAME, Direction.ASC)
                .on(ActivityLog.F_TIMESTAMP, Direction.DESC)
                .named(IndexNames.ACTIVITY_USER_TIMESTAMP));

        // loginSessions (sign-ins survive a server restart; expired ones are removed by MongoDB)
        IndexOperations logins = ops(LoginSession.class);
        logins.createIndex(new Index().on(LoginSession.F_EXPIRES_AT, Direction.ASC)
                .expire(java.time.Duration.ZERO).named(IndexNames.LOGIN_SESSIONS_EXPIRY));
        logins.createIndex(new Index().on(LoginSession.F_USERNAME, Direction.ASC)
                .named(IndexNames.LOGIN_SESSIONS_USERNAME));

        log.info("MongoDB indexes verified on database '{}'", mongoTemplate.getDb().getName());
    }

    private IndexOperations ops(Class<?> documentType) {
        return mongoTemplate.indexOps(documentType);
    }
}
