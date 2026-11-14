package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cash_boxes")
public class CashBox {
    @PrimaryKey
    @NonNull
    public String boxId;
    public String boxName;
    public String currencyCode; // YER أو SAR
    public long balance; // بالأصغر وحدة نقدية (Long)
}

@Entity(tableName = "currencies")
public class Currency {
    @PrimaryKey
    @NonNull
    public String currencyCode;
    public String currencyName;
}

@Entity(tableName = "exchange_rates")
public class ExchangeRate {
    @PrimaryKey
    @NonNull
    public String rateId;
    public String fromCurrency;
    public String toCurrency;
    public long rateValue; // سعر الصرف مخزناً بـ Long
    public String effectiveDate;
}
