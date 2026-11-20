package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "invoices",
        foreignKeys = {
                @ForeignKey(entity = BusinessPartner.class, parentColumns = "partnerId",
                        childColumns = "partnerId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = CashBox.class, parentColumns = "cashBoxId",
                        childColumns = "cashBoxId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = JournalEntry.class, parentColumns = "entryId",
                        childColumns = "journalEntryId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = Invoice.class, parentColumns = "invoiceId",
                        childColumns = "originalInvoiceId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {
                @Index(value = "idempotencyKey", unique = true),
                @Index("partnerId"),
                @Index("cashBoxId"),
                @Index("journalEntryId"),
                @Index("issuedAt"),
                @Index("type"),
                @Index("originalInvoiceId")
        })
public class Invoice {
    @PrimaryKey
    @NonNull
    public String invoiceId = "";
    @NonNull
    public String idempotencyKey = "";
    @NonNull
    public String requestFingerprint = "";
    @NonNull
    public String type = "";
    public String partnerId;
    public String cashBoxId;
    public String journalEntryId;
    public String originalInvoiceId;
    public long issuedAt;
    @NonNull
    @ColumnInfo(defaultValue = "'YER'")
    public String currencyCode = "YER";
    public long subtotal;
    @ColumnInfo(defaultValue = "0")
    public long directCosts;
    public long discount;
    public long total;
    public long paid;
    @NonNull
    @ColumnInfo(defaultValue = "'UNPAID'")
    public String paymentStatus = "UNPAID";
    @ColumnInfo(defaultValue = "0")
    public long returnedAmount;
    @NonNull
    public String status = "POSTED";
}
