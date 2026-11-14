package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "accounts")
public class Account {
    @PrimaryKey
    @NonNull
    public String accountId;
    public String accountName;
    public String accountType;
    public String parentAccountId;
    public long balance;
}
