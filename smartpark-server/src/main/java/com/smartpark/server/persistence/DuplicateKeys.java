package com.smartpark.server.persistence;

import org.springframework.dao.DuplicateKeyException;

/**
 * Identifies which unique index caused an {@code E11000 duplicate key} error.
 * MongoDB includes the index name in the message, e.g.
 * {@code "... index: ux_sessions_active_vehicle dup key: { vehicleNumber: \"ABC123\" }"}.
 */
public final class DuplicateKeys {

    private DuplicateKeys() {
    }

    public static boolean violates(DuplicateKeyException e, String indexName) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.contains(indexName)) {
                return true;
            }
        }
        return false;
    }
}
