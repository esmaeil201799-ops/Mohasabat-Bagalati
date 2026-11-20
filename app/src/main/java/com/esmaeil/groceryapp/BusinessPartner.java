package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "business_partners", indices = {@Index("type"), @Index("name")})
public class BusinessPartner {
    public static final String TYPE_CUSTOMER = "CUSTOMER";
    public static final String TYPE_SUPPLIER = "SUPPLIER";

    @PrimaryKey
    @NonNull
    public String partnerId = "";
    @NonNull
    public String name = "";
    @NonNull
    public String type = "";
    public String phone;
    public String address;
    public long createdAt;
    public boolean isActive = true;
}
