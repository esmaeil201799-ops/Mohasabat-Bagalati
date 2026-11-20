package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "invoice_lines",
        foreignKeys = {
                @ForeignKey(entity = Invoice.class, parentColumns = "invoiceId",
                        childColumns = "invoiceId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = InventoryItem.class, parentColumns = "itemId",
                        childColumns = "itemId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = InvoiceLine.class, parentColumns = "lineId",
                        childColumns = "originalLineId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {@Index("invoiceId"), @Index("itemId"), @Index("originalLineId")})
public class InvoiceLine {
    @PrimaryKey
    @NonNull
    public String lineId = "";
    @NonNull
    public String invoiceId = "";
    @NonNull
    public String itemId = "";
    public String originalLineId;
    public long quantity;
    public long unitPrice;
    public long transportPerUnit;
    public long costAtPosting;
    public long lineTotal;
    @ColumnInfo(defaultValue = "1")
    public boolean restock = true;
}
