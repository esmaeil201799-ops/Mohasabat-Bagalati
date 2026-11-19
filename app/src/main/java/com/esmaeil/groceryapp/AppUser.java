package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "app_users", indices = {@Index(value = "username", unique = true)})
public class AppUser {
    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_CASHIER = "CASHIER";

    @PrimaryKey
    @NonNull
    public String userId = "";
    @NonNull
    public String username = "";
    @NonNull
    public String role = "";
    public byte[] pinSalt;
    public byte[] pinVerifier;
    public byte[] recoverySalt;
    public byte[] recoveryVerifier;
    public boolean isActive = true;
    public long createdAt;
    @ColumnInfo(defaultValue = "0")
    public int failedPinAttempts;
    @ColumnInfo(defaultValue = "0")
    public long pinLockedUntil;
}
