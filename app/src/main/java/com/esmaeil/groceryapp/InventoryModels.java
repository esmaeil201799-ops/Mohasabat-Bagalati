package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "units")
public class Unit {
    @PrimaryKey
    @NonNull
    public String unitId;
    public String unitName; // حبة، كرتون، كيلو، جرام، لتر، ملليلتر، عبوة، كيس، علبة، سلة، جالون
}

@Entity(tableName = "unit_conversions")
public class UnitConversion {
    @PrimaryKey
    @NonNull
    public String conversionId;
    public String itemId;
    public String fromUnitId;
    public String toUnitId;
    public long factor; // معامل التحويل بدقة صحيحة (Long)
}

@Entity(tableName = "stock_movements")
public class StockMovement {
    @PrimaryKey
    @NonNull
    public String movementId;
    public String itemId;
    public String referenceId;
    public String movementType; // شراء، بيع، تلف، تسوية
    public long quantityChange; // بالوحدة الصغرى (Long)
    public long costAtTheTime;  // التكلفة وقت الحركة (Long)
    public String date;
}
