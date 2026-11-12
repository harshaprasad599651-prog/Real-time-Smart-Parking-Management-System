package com.smartpark.server.persistence;

import com.mongodb.MongoException;
import com.smartpark.server.exception.ConcurrentUpdateException;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs a multi-step write either inside a MongoDB transaction or as a sequence of
 * single-document atomic operations, depending on what the local deployment supports.
 * <ul>
 *     <li><b>Standalone mongod</b> (the default local install): transactions are NOT
 *         available. The work runs directly; each step is an atomic single-document
 *         operation and the caller must compensate if a later step fails.</li>
 *     <li><b>Replica set / sharded cluster</b>: the work runs in a transaction, so either
 *         all steps commit or none do. Transient conflicts (WriteConflict) are retried
 *         with a short random back-off.</li>
 * </ul>
 * The work receives a flag saying which mode is active, so it knows whether manual
 * compensation is needed.
 */
@Component
public class MongoTransactionRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoTransactionRunner.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final int WRITE_CONFLICT_CODE = 112;

    /** A unit of work. {@code inTransaction} tells it whether a rollback will undo its writes. */
    @FunctionalInterface
    public interface Work<T> {
        T run(boolean inTransaction);
    }

    private final MongoTemplate mongoTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String mode;
    private volatile Boolean transactionsSupported;

    public MongoTransactionRunner(MongoTemplate mongoTemplate,
                                  MongoTransactionManager transactionManager,
                                  @Value("${smartpark.mongodb.transactions:auto}") String mode) {
        this.mongoTemplate = mongoTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.mode = mode.trim().toLowerCase(Locale.ROOT);
        if (!this.mode.equals("auto") && !this.mode.equals("never")) {
            throw new IllegalArgumentException("smartpark.mongodb.transactions must be 'auto' or 'never', was: " + mode);
        }
    }

    /** True when the connected MongoDB deployment supports multi-document transactions and they are enabled. */
    public boolean transactionsSupported() {
        Boolean supported = transactionsSupported;
        if (supported == null) {
            supported = mode.equals("auto") && detectTransactionSupport();
            transactionsSupported = supported;
            log.info("MongoDB multi-document transactions: {}", supported
                    ? "ENABLED (replica set detected)"
                    : "DISABLED - using atomic single-document operations with compensation");
        }
        return supported;
    }

    public <T> T execute(Work<T> work) {
        if (!transactionsSupported()) {
            return work.run(false);
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> work.run(true));
            } catch (RuntimeException e) {
                if (!isTransient(e)) {
                    throw e;
                }
                if (attempt >= MAX_ATTEMPTS) {
                    throw new ConcurrentUpdateException(
                            "The record was changed by another operator at the same time. Please try again.", e);
                }
                log.debug("Transient transaction error (attempt {}/{}), retrying: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                backOff(attempt);
            }
        }
    }

    private boolean detectTransactionSupport() {
        Document hello = mongoTemplate.executeCommand(new Document("hello", 1));
        boolean replicaSet = hello.containsKey("setName");
        boolean mongos = "isdbgrid".equals(hello.getString("msg"));
        return replicaSet || mongos;
    }

    private static boolean isTransient(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof MongoException me
                    && (me.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                    || me.getCode() == WRITE_CONFLICT_CODE)) {
                return true;
            }
        }
        return false;
    }

    private static void backOff(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 25) * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ConcurrentUpdateException("Interrupted while retrying a transaction", ie);
        }
    }
}
