package com.smartpark.server.persistence;

import com.smartpark.server.support.AbstractMongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MongoIndexTest extends AbstractMongoIntegrationTest {

    @Test
    void usersHaveUniqueUsername() {
        assertUnique("users", IndexNames.USERS_USERNAME, new Document("username", 1));
    }

    @Test
    void categoriesHaveUniqueName() {
        assertUnique("vehicleCategories", IndexNames.CATEGORIES_NAME, new Document("categoryName", 1));
    }

    @Test
    void slotsHaveUniqueCodeAndPosition() {
        assertUnique("parkingSlots", IndexNames.SLOTS_CODE, new Document("slotCode", 1));
        assertUnique("parkingSlots", IndexNames.SLOTS_POSITION,
                new Document("floor", 1).append("row", 1).append("column", 1));
        assertThat(indexes("parkingSlots")).containsKey(IndexNames.SLOTS_STATUS_CATEGORY);
    }

    @Test
    void sessionsHaveUniqueTicketAndPartialUniqueActiveIndexes() {
        assertUnique("parkingSessions", IndexNames.SESSIONS_TICKET, new Document("ticketId", 1));

        Map<String, Document> idx = indexes("parkingSessions");
        for (String name : new String[]{IndexNames.SESSIONS_ACTIVE_VEHICLE, IndexNames.SESSIONS_ACTIVE_SLOT}) {
            Document index = idx.get(name);
            assertThat(index).as(name).isNotNull();
            assertThat(index.getBoolean("unique")).as(name + " unique").isTrue();
            assertThat(index.get("partialFilterExpression", Document.class))
                    .as(name + " partial filter").isEqualTo(new Document("status", "ACTIVE"));
        }
        assertThat(idx.get(IndexNames.SESSIONS_ACTIVE_VEHICLE).get("key", Document.class))
                .isEqualTo(new Document("vehicleNumber", 1));
        assertThat(idx.get(IndexNames.SESSIONS_ACTIVE_SLOT).get("key", Document.class))
                .isEqualTo(new Document("parkingSlotCode", 1));
        assertThat(idx).containsKeys(IndexNames.SESSIONS_VEHICLE_ENTRY, IndexNames.SESSIONS_STATUS_ENTRY);
    }

    @Test
    void activityLogsAreIndexedByTime() {
        assertThat(indexes("activityLogs"))
                .containsKeys(IndexNames.ACTIVITY_TIMESTAMP, IndexNames.ACTIVITY_USER_TIMESTAMP);
    }

    @Test
    void indexCreationIsIdempotent() {
        assertThatCode(() -> {
            indexInitializer.ensureIndexes();
            indexInitializer.ensureIndexes();
        }).doesNotThrowAnyException();
    }

    private void assertUnique(String collection, String indexName, Document expectedKey) {
        Document index = indexes(collection).get(indexName);
        assertThat(index).as(indexName + " exists").isNotNull();
        assertThat(index.getBoolean("unique", false)).as(indexName + " unique").isTrue();
        assertThat(index.get("key", Document.class)).as(indexName + " key").isEqualTo(expectedKey);
    }

    private Map<String, Document> indexes(String collection) {
        Map<String, Document> byName = new HashMap<>();
        for (Document index : mongoTemplate.getCollection(collection).listIndexes()) {
            byName.put(index.getString("name"), index);
        }
        return byName;
    }
}
