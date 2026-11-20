package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "cash_movements",
        foreignKeys = {
                @ForeignKey(entity = CashBox.class, parentColumns = "cashBoxId",
                        childColumns = "cashBoxId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {@Index("cashBoxId"), @Index("referenceType"), @Index("referenceId"), @Index("createdAt")})
public class CashMovement {
    @PrimaryKey
    @NonNull
    public String movementId = "";
    @NonNull
    public String cashBoxId = "";
    @NonNull
    public String referenceType = "";
    @NonNull
    public String referenceId = "";
    public long amount;
    public long createdAt;
}
