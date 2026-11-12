package com.smartpark.server.document;

/** Role of a system user; decides which client screens and operations are allowed. */
public enum UserRole {
    ADMIN,
    ENTRY_OPERATOR,
    EXIT_OPERATOR
}
