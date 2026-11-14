package com.esmaeil.groceryapp;

import androidx.room.RoomDatabase;
import java.util.UUID;

public class AccountingEngine {

    /**
     * ينشئ قيداً محاسبياً مزدوجاً ومتوازناً (مدين = دائن) داخل معاملة ذرية (Transaction)
     */
    public static void postBalancedEntry(RoomDatabase db, String date, String description, 
                                         long debitAmount, String debitAccountId, 
                                         long creditAmount, String creditAccountId) {
        if (debitAmount != creditAmount) {
            throw new IllegalArgumentException("فشل ترحيل القيد: إجمالي المدين لا يساوي إجمالي الدائن!");
        }

        db.runInTransaction(() -> {
            String entryId = "ENT-" + UUID.randomUUID().toString();
            
            JournalEntry entry = new JournalEntry();
            entry.entryId = entryId;
            entry.date = date;
            entry.description = description;
            entry.totalDebit = debitAmount;
            entry.totalCredit = creditAmount;
            entry.isPosted = true;

            JournalLine debitLine = new JournalLine();
            debitLine.lineId = "LNE-" + UUID.randomUUID().toString();
            debitLine.entryId = entryId;
            debitLine.accountId = debitAccountId;
            debitLine.debit = debitAmount;
            debitLine.credit = 0L;

            JournalLine creditLine = new JournalLine();
            creditLine.lineId = "LNE-" + UUID.randomUUID().toString();
            creditLine.entryId = entryId;
            creditLine.accountId = creditAccountId;
            creditLine.debit = 0L;
            creditLine.credit = creditAmount;
        });
    }
}
