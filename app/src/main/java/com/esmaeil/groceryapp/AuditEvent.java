package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "audit_events",
        foreignKeys = {
                @ForeignKey(entity = AppUser.class, parentColumns = "userId",
                        childColumns = "userId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {@Index("userId"), @Index("createdAt"), @Index("entityType"), @Index("entityId")})
public class AuditEvent {
    @PrimaryKey
    @NonNull
    public String eventId = "";
    public String userId;
    @NonNull
    public String action = "";
    @NonNull
    public String entityType = "";
    @NonNull
    public String entityId = "";
    @NonNull
    public String details = "";
    public long createdAt;
}
