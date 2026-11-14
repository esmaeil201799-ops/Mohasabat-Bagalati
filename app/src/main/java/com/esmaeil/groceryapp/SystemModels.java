package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "expenses")
public class Expense {
    @PrimaryKey
    @NonNull
    public String expenseId;
    public String expenseName;
    public long amount;
    public String date;
    public String boxId;
}

@Entity(tableName = "drawings")
public class Drawing {
    @PrimaryKey
    @NonNull
    public String drawingId;
    public String description;
    public long amount;
    public String date;
    public String type; // نقدية أو عينية
}

@Entity(tableName = "damages")
public class Damage {
    @PrimaryKey
    @NonNull
    public String damageId;
    public String itemId;
    public long quantity;
    public long lossValue;
    public String date;
    public String reason; // تلف أو منتهي الصلاحية
}

@Entity(tableName = "system_settings")
public class SystemSettings {
    @PrimaryKey
    @NonNull
    public String settingKey;
    public String settingValue;
}

@Entity(tableName = "users")
public class User {
    @PrimaryKey
    @NonNull
    public String userId;
    public String username;
    public String role;
}

@Entity(tableName = "audit_logs")
public class AuditLog {
    @PrimaryKey
    @NonNull
    public String logId;
    public String userId;
    public String action;
    public String timestamp;
}
