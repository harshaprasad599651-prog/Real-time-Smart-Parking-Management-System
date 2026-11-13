package com.smartpark.server.support;

import com.smartpark.server.security.LoginAttemptLimiter;
import com.smartpark.server.bootstrap.DataSeeder;
import com.smartpark.server.persistence.MongoIndexInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for tests that run against a real local MongoDB.
 * <ul>
 *     <li>Uses the separate database {@code smartpark_test_db}, never {@code smartpark_db}.</li>
 *     <li>Empties every collection before each test but keeps the indexes.</li>
 *     <li>Turns off automatic seeding and the reconciliation job, so tests control all data.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ExtendWith(MongoAvailableCondition.class)
public abstract class AbstractMongoIntegrationTest {

    public static final String TEST_DATABASE = "smartpark_test_db";

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected MongoIndexInitializer indexInitializer;

    @Autowired
    protected DataSeeder dataSeeder;

    @Autowired
    protected LoginAttemptLimiter loginAttemptLimiter;

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MongoAvailableCondition::testUri);
        registry.add("spring.mongodb.database", () -> TEST_DATABASE);
        registry.add("smartpark.seed.enabled", () -> "false");
        registry.add("smartpark.parking.reconcile-enabled", () -> "false");
        registry.add("smartpark.security.bcrypt-strength", () -> "4"); // fast hashing in tests
    }

    @BeforeEach
    void cleanDatabase() {
        for (String collection : mongoTemplate.getCollectionNames()) {
            if (!collection.startsWith("system.")) {
                mongoTemplate.remove(new Query(), collection);
            }
        }
        indexInitializer.ensureIndexes();
        loginAttemptLimiter.clear();
    }

    /** Loads the standard sample users, categories and 30 slots. */
    protected void seedSampleData() {
        dataSeeder.seed();
    }
}
