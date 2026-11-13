package com.smartpark.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB client settings.
 * <p>
 * <b>Timeouts.</b> The driver's defaults wait up to 30 s for a server and have no read timeout,
 * so with the database down a terminal would hang for half a minute per request. These bounds make
 * a database failure show up within a few seconds as a clear 503 {@code DATABASE_UNAVAILABLE}
 * instead, while the driver keeps reconnecting in the background:
 * <pre>
 *   smartpark.mongodb.server-selection-timeout=PT5S   find a usable server
 *   smartpark.mongodb.connect-timeout=PT5S            open a TCP connection
 *   smartpark.mongodb.socket-timeout=PT15S            wait for one reply
 *   smartpark.mongodb.max-wait-time=PT5S              wait for a free pooled connection
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
public class MongoConfig {

    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }

    @Bean
    MongoClientSettingsBuilderCustomizer smartparkMongoTimeouts(
            @Value("${smartpark.mongodb.server-selection-timeout:PT5S}") Duration serverSelection,
            @Value("${smartpark.mongodb.connect-timeout:PT5S}") Duration connect,
            @Value("${smartpark.mongodb.socket-timeout:PT15S}") Duration socket,
            @Value("${smartpark.mongodb.max-wait-time:PT5S}") Duration maxWait) {
        return settings -> settings
                .applyToClusterSettings(c -> c.serverSelectionTimeout(serverSelection.toMillis(), TimeUnit.MILLISECONDS))
                .applyToSocketSettings(s -> s.connectTimeout((int) connect.toMillis(), TimeUnit.MILLISECONDS)
                        .readTimeout((int) socket.toMillis(), TimeUnit.MILLISECONDS))
                .applyToConnectionPoolSettings(p -> p.maxWaitTime(maxWait.toMillis(), TimeUnit.MILLISECONDS));
    }
}
