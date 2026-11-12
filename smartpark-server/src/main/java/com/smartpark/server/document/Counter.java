package com.smartpark.server.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Internal sequence counter, incremented atomically with {@code $inc}.
 * Used to generate human-readable ticket numbers.
 */
@Document(collection = Counter.COLLECTION)
public class Counter {

    public static final String COLLECTION = "counters";
    public static final String F_SEQ = "seq";

    @Id
    private String id;
    private long seq;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
}
