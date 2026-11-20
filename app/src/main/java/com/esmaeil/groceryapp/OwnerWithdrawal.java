package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "owner_withdrawals",
        foreignKeys = {
                @ForeignKey(entity = InventoryItem.class, parentColumns = "itemId",
                        childColumns = "itemId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = CashBox.class, parentColumns = "cashBoxId",
                        childColumns = "cashBoxId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = JournalEntry.class, parentColumns = "entryId",
                        childColumns = "journalEntryId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {
                @Index(value = "idempotencyKey", unique = true),
                @Index("itemId"), @Index("cashBoxId"),
                @Index("journalEntryId"), @Index("createdAt")
        })
public class OwnerWithdrawal {
    public static final String KIND_CASH = "CASH";
    public static final String KIND_IN_KIND = "IN_KIND";

    @PrimaryKey
    @NonNull
    public String withdrawalId = "";
    @NonNull
    public String idempotencyKey = "";
    @NonNull
    public String kind = "";
    public String itemId;
    public String cashBoxId;
    @NonNull
    public String journalEntryId = "";
    public long amount;
    public long quantity;
    @NonNull
    @ColumnInfo(defaultValue = "'YER'")
    public String currencyCode = "YER";
    public long createdAt;
}
