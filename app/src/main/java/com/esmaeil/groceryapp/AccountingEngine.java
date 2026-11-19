package com.esmaeil.groceryapp;

import java.math.BigInteger;
import java.util.List;

public class AccountingEngine {
    public boolean validateBalancedEntry(long totalDebit, long totalCredit) {
        return totalDebit == totalCredit && totalDebit > 0;
    }

    static void postLines(AppDatabase database, JournalEntry entry, List<JournalLine> lines) {
        if (!database.inTransaction() || entry.isPosted || lines == null || lines.isEmpty()) {
            throw new IllegalStateException("يجب ترحيل القيد داخل معاملة وبسطور غير مرحلة");
        }
        BigInteger debit = BigInteger.ZERO;
        BigInteger credit = BigInteger.ZERO;
        for (JournalLine line : lines) {
            if (line.debit < 0 || line.credit < 0
                    || (line.debit == 0) == (line.credit == 0)) {
                throw new IllegalArgumentException("كل سطر قيد يجب أن يحتوي مدينًا أو دائنًا موجبًا فقط");
            }
            debit = debit.add(BigInteger.valueOf(line.debit));
            credit = credit.add(BigInteger.valueOf(line.credit));
        }
        if (!debit.equals(credit) || debit.signum() <= 0
                || !debit.equals(BigInteger.valueOf(entry.totalDebit))
                || !credit.equals(BigInteger.valueOf(entry.totalCredit))) {
            throw new IllegalStateException("رُفض ترحيل قيد غير متوازن");
        }
        database.accountDao().insertJournalLines(lines);
        entry.isPosted = true;
        if (database.accountDao().updateJournalEntry(entry) != 1) {
            throw new IllegalStateException("تعذر اعتماد القيد المحاسبي");
        }
    }
}
