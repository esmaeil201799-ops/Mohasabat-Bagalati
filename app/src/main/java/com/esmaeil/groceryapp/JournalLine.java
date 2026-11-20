package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "journal_lines",
        foreignKeys = {
                @ForeignKey(entity = JournalEntry.class, parentColumns = "entryId",
                        childColumns = "entryId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = Account.class, parentColumns = "accountId",
                        childColumns = "accountId", onDelete = ForeignKey.RESTRICT),
                @ForeignKey(entity = BusinessPartner.class, parentColumns = "partnerId",
                        childColumns = "partnerId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {@Index("entryId"), @Index("accountId"), @Index("partnerId")})
public class JournalLine {
    @PrimaryKey
    @NonNull
    public String lineId = "";
    @NonNull
    public String entryId = "";
    @NonNull
    public String accountId = "";
    public String partnerId;
    public long debit;
    public long credit;
}
