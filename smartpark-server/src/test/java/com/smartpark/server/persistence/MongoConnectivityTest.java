package com.smartpark.server.persistence;

import com.smartpark.server.support.AbstractMongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class MongoConnectivityTest extends AbstractMongoIntegrationTest {

    @Autowired
    private MongoTransactionRunner transactionRunner;

    @Test
    void pingsLocalMongoDb() {
        Document reply = mongoTemplate.executeCommand(new Document("ping", 1));

        assertThat(reply.get("ok", Number.class).doubleValue()).isEqualTo(1.0);
    }

    @Test
    void usesTheIsolatedTestDatabase() {
        assertThat(mongoTemplate.getDb().getName()).isEqualTo(TEST_DATABASE);
    }

    @Test
    void detectsTransactionSupportFromDeploymentType() {
        Document hello = mongoTemplate.executeCommand(new Document("hello", 1));
        boolean replicaSet = hello.containsKey("setName");

        assertThat(transactionRunner.transactionsSupported()).isEqualTo(replicaSet);
    }

    @Test
    void createsAllCollections() {
        assertThat(mongoTemplate.getCollectionNames())
                .contains("users", "vehicleCategories", "parkingSlots", "parkingSessions", "activityLogs");
    }
}
