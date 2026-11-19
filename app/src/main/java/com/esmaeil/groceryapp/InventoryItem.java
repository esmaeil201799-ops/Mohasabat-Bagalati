package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "inventory_items", indices = {@Index(value = "barcode", unique = true)})
public class InventoryItem {
    @PrimaryKey
    @NonNull
    public String itemId;
    public String itemName;
    public String barcode;
    public String category;
    public String baseUnitId;
    public long quantity; // بالوحدة الصغرى المعيارية
    public long purchasePriceAverage; // باللونغ لأصغر وحدة نقدية
    public long sellingPrice;
    public long minStockAlert;
    public String expiryDate;
    @NonNull
    @ColumnInfo(defaultValue = "'YER'")
    public String currencyCode = "YER";
    @ColumnInfo(defaultValue = "1")
    public boolean isActive = true;
}
