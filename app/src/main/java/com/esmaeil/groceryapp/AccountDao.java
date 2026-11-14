package com.esmaeil.groceryapp;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface AccountDao {
    @Insert
    void insertAccount(Account account);

    @Query("SELECT * FROM accounts")
    List<Account> getAllAccounts();
}
