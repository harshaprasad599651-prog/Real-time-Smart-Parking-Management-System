package com.smartpark.server.service;

import com.smartpark.server.document.Counter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Generates readable, unique ticket numbers such as {@code SP-20261112-000042}.
 * <p>
 * The number comes from a per-day counter document that is incremented with an atomic
 * {@code findAndModify + $inc}, so concurrent entry operators never get the same
 * number. The unique index on {@code parkingSessions.ticketId} is a second safety net.
 */
@Component
public class TicketIdGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final MongoTemplate mongoTemplate;
    private final ZoneId zone = ZoneId.systemDefault();

    public TicketIdGenerator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String next(Instant now) {
        String day = LocalDate.ofInstant(now, zone).format(DAY);
        String counterId = "ticket-" + day;
        Counter counter;
        try {
            counter = increment(counterId);
        } catch (DuplicateKeyException e) {
            // Two first-of-the-day upserts raced; the counter now exists, so retry once.
            counter = increment(counterId);
        }
        return "SP-%s-%06d".formatted(day, counter.getSeq());
    }

    private Counter increment(String counterId) {
        return mongoTemplate.findAndModify(
                query(where("_id").is(counterId)),
                new Update().inc(Counter.F_SEQ, 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Counter.class);
    }
}
