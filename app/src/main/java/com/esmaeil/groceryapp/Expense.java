package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "expenses",
        foreignKeys = {
                @ForeignKey(entity = CashBox.class, parentColumns = "cashBoxId",
                        childColumns = "cashBoxId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = JournalEntry.class, parentColumns = "entryId",
                        childColumns = "journalEntryId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {
                @Index(value = "idempotencyKey", unique = true),
                @Index("cashBoxId"), @Index("journalEntryId"), @Index("createdAt")
        })
public class Expense {
    @PrimaryKey
    @NonNull
    public String expenseId = "";
    @NonNull
    public String idempotencyKey = "";
    @NonNull
    public String description = "";
    @NonNull
    public String cashBoxId = "";
    @NonNull
    public String journalEntryId = "";
    @NonNull
    @ColumnInfo(defaultValue = "'YER'")
    public String currencyCode = "YER";
    public long amount;
    public long createdAt;
}
