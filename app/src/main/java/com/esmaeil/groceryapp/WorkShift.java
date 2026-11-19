package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "work_shifts",
        foreignKeys = {
                @ForeignKey(entity = AppUser.class, parentColumns = "userId",
                        childColumns = "userId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = CashBox.class, parentColumns = "cashBoxId",
                        childColumns = "cashBoxId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {@Index("userId"), @Index("cashBoxId"), @Index("openedAt")})
public class WorkShift {
    @PrimaryKey
    @NonNull
    public String shiftId = "";
    @NonNull
    public String userId = "";
    @NonNull
    public String cashBoxId = "";
    public long openedAt;
    public Long closedAt;
    public long openingCash;
    public Long expectedCash;
    public Long actualCash;
    @NonNull
    public String status = "OPEN";
}
