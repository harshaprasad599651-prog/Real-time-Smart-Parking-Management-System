package com.smartpark.server.scheduler;

import com.smartpark.server.service.ParkingSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically repairs slots left OCCUPIED without an ACTIVE session, which can only
 * happen after a crash on a standalone MongoDB (no transactions).
 * Disable with {@code smartpark.parking.reconcile-enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "smartpark.parking.reconcile-enabled", havingValue = "true", matchIfMissing = true)
public class SlotReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(SlotReconciliationJob.class);

    private final ParkingSessionService parkingSessionService;

    public SlotReconciliationJob(ParkingSessionService parkingSessionService) {
        this.parkingSessionService = parkingSessionService;
    }

    @Scheduled(initialDelayString = "${smartpark.parking.reconcile-initial-delay:PT30S}",
            fixedDelayString = "${smartpark.parking.reconcile-interval:PT1M}")
    public void reconcile() {
        try {
            int released = parkingSessionService.reconcileOrphanedSlots();
            if (released > 0) {
                log.warn("Reconciliation released {} orphaned slot(s)", released);
            }
        } catch (RuntimeException e) {
            log.error("Slot reconciliation failed", e);
        }
    }
}
