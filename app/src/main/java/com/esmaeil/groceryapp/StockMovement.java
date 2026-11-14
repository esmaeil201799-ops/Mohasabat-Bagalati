package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "stock_movements")
public class StockMovement {
    @PrimaryKey
    @NonNull
    public String movementId;
    public String itemId;
    public String referenceId;
    public String movementType;
    public long quantityChange;
    public long costAtTheTime;
    public String date;
}
