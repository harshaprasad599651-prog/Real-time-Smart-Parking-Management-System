package com.smartpark.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * MongoDB configuration beyond Spring Boot's auto-configuration.
 * <p>
 * The transaction manager is always defined but only used when
 * {@link com.smartpark.server.persistence.MongoTransactionRunner} detects a deployment
 * that supports transactions (replica set). On a standalone server it stays unused.
 */
@Configuration(proxyBeanMethods = false)
public class MongoConfig {

    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }
}
