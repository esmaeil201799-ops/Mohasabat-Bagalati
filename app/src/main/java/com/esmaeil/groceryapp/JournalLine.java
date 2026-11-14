package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "journal_lines")
public class JournalLine {
    @PrimaryKey
    @NonNull
    public String lineId;
    public String entryId;
    public String accountId;
    public long debit;
    public long credit;
}
