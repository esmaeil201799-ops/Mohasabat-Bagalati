package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "payments",
        foreignKeys = {
                @ForeignKey(entity = Invoice.class, parentColumns = "invoiceId",
                        childColumns = "invoiceId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = BusinessPartner.class, parentColumns = "partnerId",
                        childColumns = "partnerId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = CashBox.class, parentColumns = "cashBoxId",
                        childColumns = "cashBoxId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = JournalEntry.class, parentColumns = "entryId",
                        childColumns = "journalEntryId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {
                @Index(value = "idempotencyKey", unique = true),
                @Index("invoiceId"), @Index("partnerId"),
                @Index("cashBoxId"), @Index("journalEntryId"), @Index("createdAt")
        })
public class Payment {
    @PrimaryKey
    @NonNull
    public String paymentId = "";
    @NonNull
    public String idempotencyKey = "";
    @NonNull
    public String direction = "";
    @NonNull
    @ColumnInfo(defaultValue = "'YER'")
    public String currencyCode = "YER";
    public String invoiceId;
    public String partnerId;
    public String cashBoxId;
    public String journalEntryId;
    public long amount;
    public long discount;
    public long createdAt;
}
