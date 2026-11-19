package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "stock_movements",
        foreignKeys = {
                @ForeignKey(entity = InventoryItem.class, parentColumns = "itemId",
                        childColumns = "itemId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {@Index("itemId"), @Index("referenceId"), @Index("date")})
public class StockMovement {
    @PrimaryKey
    @NonNull
    public String movementId = "";
    public String itemId;
    public String referenceId;
    public String movementType;
    public long quantityChange;
    public long costAtTheTime;
    @NonNull
    @ColumnInfo(defaultValue = "'YER'")
    public String currencyCode = "YER";
    public String date;
}
