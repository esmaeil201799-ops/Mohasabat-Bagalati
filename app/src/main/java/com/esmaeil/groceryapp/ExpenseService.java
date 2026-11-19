package com.esmaeil.groceryapp;

import java.util.Arrays;
import java.util.UUID;

public final class ExpenseService {
    private final AppDatabase database;

    public ExpenseService(AppDatabase database) {
        this.database = database;
    }

    public Expense postExpense(String description, long amount, String currencyCode,
                               String idempotencyKey) {
        AppUser actor = SessionManager.require(database, SessionManager.Permission.POST_EXPENSE);
        if (description == null || description.trim().isEmpty() || amount <= 0
                || idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("بيان المصروف ومبلغه ومفتاح العملية مطلوبة");
        }
        MoneyCurrency.requireSupported(currencyCode);
        String normalizedDescription = description.trim();
        Expense[] result = new Expense[1];
        database.runInTransaction(() -> {
            Expense previous = database.erpDao().getExpenseByIdempotencyKey(idempotencyKey);
            if (previous != null) {
                if (!normalizedDescription.equals(previous.description)
                        || previous.amount != amount
                        || !currencyCode.equals(previous.currencyCode)) {
                    throw new IllegalStateException("مفتاح العملية مستخدم لمصروف مختلف");
                }
                result[0] = previous;
                return;
            }
            long timestamp = System.currentTimeMillis();
            if (database.erpDao().getCashBalance(currencyCode) < amount) {
                throw new IllegalStateException("رصيد الصندوق غير كافٍ للمصروف");
            }
            CashBox cashBox = AccountingAccounts.cashBox(database, currencyCode, timestamp);
            String cashAccount = AccountingAccounts.account(database, "system-cash",
                    "الصندوق", "ASSET", currencyCode);
            String expenseAccount = AccountingAccounts.account(database,
                    "system-operating-expenses", "مصروفات تشغيلية", "EXPENSE", currencyCode);
            String entryId = UUID.randomUUID().toString();
            JournalEntry entry = AccountingAccounts.newEntry(database,
                    "مصروف " + normalizedDescription, "EXPENSE", idempotencyKey, amount);
            Expense expense = new Expense();
            expense.expenseId = UUID.randomUUID().toString();
            expense.idempotencyKey = idempotencyKey;
            expense.description = normalizedDescription;
            expense.cashBoxId = cashBox.cashBoxId;
            expense.journalEntryId = entryId;
            expense.currencyCode = currencyCode;
            expense.amount = amount;
            expense.createdAt = timestamp;
            database.erpDao().insertExpense(expense);

            CashMovement movement = new CashMovement();
            movement.movementId = UUID.randomUUID().toString();
            movement.cashBoxId = cashBox.cashBoxId;
            movement.referenceType = "EXPENSE";
            movement.referenceId = expense.expenseId;
            movement.amount = -amount;
            movement.createdAt = timestamp;
            database.erpDao().insertCashMovement(movement);

            AccountingEngine.postLines(database, entry, Arrays.asList(
                    AccountingAccounts.line(entryId, expenseAccount, null, amount, 0),
                    AccountingAccounts.line(entryId, cashAccount, null, 0, amount)));
            AccountingAccounts.audit(database, actor.userId, "POST", "EXPENSE",
                    expense.expenseId, "اعتماد مصروف تشغيلي " + normalizedDescription, timestamp);
            result[0] = expense;
        });
        return result[0];
    }
}
