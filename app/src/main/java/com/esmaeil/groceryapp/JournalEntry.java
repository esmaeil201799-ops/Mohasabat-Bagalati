package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "journal_entries")
public class JournalEntry {
    @PrimaryKey
    @NonNull
    public String entryId;
    public String date;
    public String description;
    public String referenceType;
    public String referenceId;
    public long totalDebit;
    public long totalCredit;
    public boolean isPosted;
}
