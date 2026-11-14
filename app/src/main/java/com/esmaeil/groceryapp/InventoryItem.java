package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "inventory_items")
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
}
