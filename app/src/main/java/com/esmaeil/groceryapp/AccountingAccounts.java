package com.esmaeil.groceryapp;

import java.util.UUID;

final class AccountingAccounts {
    private AccountingAccounts() {}

    static String account(AppDatabase database, String baseId, String name,
                          String type, String currencyCode) {
        String accountId = MoneyCurrency.accountId(baseId, currencyCode);
        if (database.accountDao().getAccount(accountId) == null) {
            Account account = new Account();
            account.accountId = accountId;
            account.accountName = name + " (" + currencyCode + ")";
            account.accountType = type;
            database.accountDao().insertAccountIfMissing(account);
        }
        return accountId;
    }

    static CashBox cashBox(AppDatabase database, String currencyCode, long timestamp) {
        MoneyCurrency.requireSupported(currencyCode);
        CashBox cashBox = database.erpDao().getActiveCashBoxForCurrency(currencyCode);
        if (cashBox != null) {
            return cashBox;
        }
        String id = MoneyCurrency.cashBoxId(currencyCode);
        CashBox existing = database.erpDao().getCashBox(id);
        if (existing != null && !existing.isActive) {
            throw new IllegalStateException("صندوق العملة غير نشط");
        }
        if (existing != null) {
            return existing;
        }
        CashBox created = new CashBox();
        created.cashBoxId = id;
        created.name = "الصندوق الرئيسي - " + currencyCode;
        created.currencyCode = currencyCode;
        created.createdAt = timestamp;
        database.erpDao().insertCashBox(created);
        return created;
    }

    static JournalEntry newEntry(AppDatabase database, String description,
                                 String referenceType, String referenceId,
                                 long amount) {
        JournalEntry entry = new JournalEntry();
        entry.entryId = UUID.randomUUID().toString();
        entry.date = AppDateTime.formatUtc(System.currentTimeMillis());
        entry.description = description;
        entry.referenceType = referenceType;
        entry.referenceId = referenceId;
        entry.totalDebit = amount;
        entry.totalCredit = amount;
        entry.isPosted = false;
        database.accountDao().insertJournalEntry(entry);
        return entry;
    }

    static JournalLine line(String entryId, String accountId, String partnerId,
                            long debit, long credit) {
        JournalLine line = new JournalLine();
        line.lineId = UUID.randomUUID().toString();
        line.entryId = entryId;
        line.accountId = accountId;
        line.partnerId = partnerId;
        line.debit = debit;
        line.credit = credit;
        return line;
    }

    static void audit(AppDatabase database, String userId, String action,
                      String entityType, String entityId, String details, long timestamp) {
        AuditEvent event = new AuditEvent();
        event.eventId = UUID.randomUUID().toString();
        event.userId = userId;
        event.action = action;
        event.entityType = entityType;
        event.entityId = entityId;
        event.details = details;
        event.createdAt = timestamp;
        database.erpDao().insertAuditEvent(event);
    }
}
