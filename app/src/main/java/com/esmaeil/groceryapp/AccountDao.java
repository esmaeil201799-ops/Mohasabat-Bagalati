package com.esmaeil.groceryapp;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface AccountDao {
    @Insert
    void insertAccount(Account account);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertAccountIfMissing(Account account);

    @Query("SELECT * FROM accounts")
    List<Account> getAllAccounts();

    @Query("SELECT * FROM accounts WHERE accountId = :accountId LIMIT 1")
    Account getAccount(String accountId);

    @Insert
    void insertJournalEntry(JournalEntry entry);

    @Update
    int updateJournalEntry(JournalEntry entry);

    @Insert
    void insertJournalLines(List<JournalLine> lines);

    @Query("SELECT * FROM journal_entries WHERE entryId = :entryId LIMIT 1")
    JournalEntry getJournalEntry(String entryId);
}
