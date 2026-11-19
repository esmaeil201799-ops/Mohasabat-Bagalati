package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "legacy_archive", primaryKeys = {"dataset", "recordKey"})
public class LegacyRecord {
    @NonNull
    public String dataset = "";
    @NonNull
    public String recordKey = "";
    @NonNull
    public String payload = "";
    public long importedAt;
}
