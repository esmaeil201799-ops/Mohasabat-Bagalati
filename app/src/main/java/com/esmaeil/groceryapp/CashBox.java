package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "cash_boxes", indices = {@Index("name")})
public class CashBox {
    @PrimaryKey
    @NonNull
    public String cashBoxId = "";
    @NonNull
    public String name = "";
    @NonNull
    @ColumnInfo(defaultValue = "'YER'")
    public String currencyCode = "YER";
    public boolean isActive = true;
    public long createdAt;
}
