package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "units")
public class Unit {
    @PrimaryKey
    @NonNull
    public String unitId;
    public String unitName;
}
