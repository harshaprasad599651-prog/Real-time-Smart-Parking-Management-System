package com.smartpark.server.repository;

import com.smartpark.server.document.ActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ActivityLogRepository extends MongoRepository<ActivityLog, String> {

    List<ActivityLog> findAllByOrderByTimestampDesc(Pageable pageable);

    List<ActivityLog> findByUsernameOrderByTimestampDesc(String username, Pageable pageable);
}
