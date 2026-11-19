package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "unit_conversions",
        foreignKeys = {
                @ForeignKey(entity = InventoryItem.class, parentColumns = "itemId",
                        childColumns = "itemId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = Unit.class, parentColumns = "unitId",
                        childColumns = "unitId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {
                @Index(value = {"itemId", "unitId"}, unique = true),
                @Index("unitId")
        })
public class UnitConversion {
    @PrimaryKey
    @NonNull
    public String conversionId = "";
    @NonNull
    public String itemId = "";
    @NonNull
    public String unitId = "";
    public long baseUnitsPerUnit;
}
