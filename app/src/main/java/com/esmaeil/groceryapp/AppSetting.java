package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "app_settings")
public class AppSetting {
    @PrimaryKey
    @NonNull
    public String settingKey = "";
    @NonNull
    public String settingValue = "";
}
