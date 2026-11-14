package com.esmaeil.groceryapp;

public class AccountingEngine {
    // محرك القيود المحاسبية المزدوجة ونظام الأستاذ العام
    private final AppDatabase database;

    public AccountingEngine(AppDatabase database) {
        this.database = database;
    }

    // التحقق من توازن القيد المحاسبي (مدين = دائن) بدقة مالية تامة
    public boolean validateBalancedEntry(long totalDebit, long totalCredit) {
        return totalDebit == totalCredit && totalDebit > 0;
    }
}
