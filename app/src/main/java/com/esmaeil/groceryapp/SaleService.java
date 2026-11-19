package com.esmaeil.groceryapp;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SaleService {
    private static final String CASH_ACCOUNT = "system-cash";
    private static final String RECEIVABLES_ACCOUNT = "system-receivables";
    private static final String SALES_ACCOUNT = "system-sales";
    private static final String COGS_ACCOUNT = "system-cost-of-sales";
    private static final String INVENTORY_ACCOUNT = "system-inventory";

    private final AppDatabase database;

    public SaleService(AppDatabase database) {
        this.database = database;
    }

    public Invoice postSale(String itemId, long quantity, boolean onCredit,
                            String customerName, String idempotencyKey) {
        return postSale(itemId, quantity, onCredit, customerName, MoneyCurrency.YER,
                idempotencyKey);
    }

    public Invoice postSale(String itemId, long quantity, boolean onCredit,
                            String customerName, String currencyCode, String idempotencyKey) {
        AppUser actor = SessionManager.require(database, SessionManager.Permission.POST_SALE);
        if (itemId == null || itemId.trim().isEmpty() || quantity <= 0
                || idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("الصنف والكمية ومفتاح العملية مطلوبة");
        }
        MoneyCurrency.requireSupported(currencyCode);
        String normalizedCustomer = customerName == null ? "" : customerName.trim();
        if (onCredit && normalizedCustomer.isEmpty()) {
            throw new IllegalArgumentException("اسم العميل مطلوب للبيع الآجل");
        }
        String fingerprint = itemId + "|" + quantity + "|" + (onCredit ? "CREDIT" : "CASH")
                + "|" + normalizedCustomer.toLowerCase(java.util.Locale.ROOT)
                + "|" + currencyCode;
        Invoice[] result = new Invoice[1];

        database.runInTransaction(() -> {
            Invoice previous = database.erpDao().getInvoiceByIdempotencyKey(idempotencyKey);
            if (previous != null) {
                if (!fingerprint.equals(previous.requestFingerprint)) {
                    throw new IllegalStateException("مفتاح العملية مستخدم لطلب بيع مختلف");
                }
                result[0] = previous;
                return;
            }

            InventoryItem item = database.inventoryDao().getItem(itemId);
            if (item == null || !item.isActive) {
                throw new IllegalArgumentException("الصنف غير موجود أو غير نشط");
            }
            if (!currencyCode.equals(item.currencyCode)) {
                throw new IllegalArgumentException("عملة الصنف لا تطابق عملة البيع");
            }
            if (item.sellingPrice <= 0 || item.purchasePriceAverage < 0
                    || item.quantity < quantity) {
                throw new IllegalStateException("السعر غير صالح أو الكمية المتاحة غير كافية");
            }

            long total = FinancialMath.multiply(item.sellingPrice, quantity);
            long cost = FinancialMath.multiply(item.purchasePriceAverage, quantity);
            long debitTotal = FinancialMath.add(total, cost);
            long timestamp = System.currentTimeMillis();
            String invoiceId = UUID.randomUUID().toString();
            String entryId = UUID.randomUUID().toString();
            String partnerId = null;
            if (onCredit) {
                BusinessPartner partner = database.erpDao().getActivePartnerByName(
                        BusinessPartner.TYPE_CUSTOMER, normalizedCustomer);
                if (partner == null) {
                    partner = new BusinessPartner();
                    partner.partnerId = UUID.randomUUID().toString();
                    partner.name = normalizedCustomer;
                    partner.type = BusinessPartner.TYPE_CUSTOMER;
                    partner.createdAt = timestamp;
                    database.erpDao().insertPartner(partner);
                }
                partnerId = partner.partnerId;
            }

            String cashBoxId = MoneyCurrency.cashBoxId(currencyCode);
            CashBox cashBox = database.erpDao().getCashBox(cashBoxId);
            if (cashBox == null) {
                cashBox = new CashBox();
                cashBox.cashBoxId = cashBoxId;
                cashBox.name = MoneyCurrency.YER.equals(currencyCode)
                        ? "الصندوق الرئيسي - YER" : "الصندوق الرئيسي - SAR";
                cashBox.currencyCode = currencyCode;
                cashBox.createdAt = timestamp;
                database.erpDao().insertCashBox(cashBox);
            } else if (!cashBox.isActive) {
                throw new IllegalStateException("الصندوق الرئيسي غير نشط");
            }
            String cashAccount = accountId(CASH_ACCOUNT, currencyCode);
            String receivablesAccount = accountId(RECEIVABLES_ACCOUNT, currencyCode);
            String salesAccount = accountId(SALES_ACCOUNT, currencyCode);
            String cogsAccount = accountId(COGS_ACCOUNT, currencyCode);
            String inventoryAccount = accountId(INVENTORY_ACCOUNT, currencyCode);
            ensureAccount(cashAccount, "الصندوق", "ASSET", currencyCode);
            ensureAccount(receivablesAccount, "ذمم العملاء", "ASSET", currencyCode);
            ensureAccount(salesAccount, "إيرادات المبيعات", "INCOME", currencyCode);
            ensureAccount(cogsAccount, "تكلفة البضاعة المباعة", "EXPENSE", currencyCode);
            ensureAccount(inventoryAccount, "المخزون", "ASSET", currencyCode);

            JournalEntry journal = new JournalEntry();
            journal.entryId = entryId;
            journal.date = AppDateTime.formatUtc(timestamp);
            journal.description = "ترحيل فاتورة بيع " + invoiceId;
            journal.referenceType = "SALE";
            journal.referenceId = invoiceId;
            journal.totalDebit = debitTotal;
            journal.totalCredit = debitTotal;
            journal.isPosted = false;
            database.accountDao().insertJournalEntry(journal);

            Invoice invoice = new Invoice();
            invoice.invoiceId = invoiceId;
            invoice.idempotencyKey = idempotencyKey;
            invoice.requestFingerprint = fingerprint;
            invoice.type = onCredit ? "SALE_CREDIT" : "SALE_CASH";
            invoice.partnerId = partnerId;
            invoice.cashBoxId = cashBox.cashBoxId;
            invoice.journalEntryId = entryId;
            invoice.currencyCode = currencyCode;
            invoice.issuedAt = timestamp;
            invoice.subtotal = total;
            invoice.total = total;
            invoice.paid = onCredit ? 0 : total;
            invoice.paymentStatus = onCredit ? "UNPAID" : "PAID";
            invoice.status = "POSTED";
            database.erpDao().insertInvoice(invoice);

            InvoiceLine line = new InvoiceLine();
            line.lineId = UUID.randomUUID().toString();
            line.invoiceId = invoiceId;
            line.itemId = itemId;
            line.quantity = quantity;
            line.unitPrice = item.sellingPrice;
            line.costAtPosting = item.purchasePriceAverage;
            line.lineTotal = total;
            database.erpDao().insertInvoiceLines(java.util.Collections.singletonList(line));

            if (database.inventoryDao().decreaseStock(itemId, quantity) != 1) {
                throw new IllegalStateException("تغير المخزون أثناء البيع؛ لم تُحفظ الفاتورة");
            }
            StockMovement movement = new StockMovement();
            movement.movementId = UUID.randomUUID().toString();
            movement.itemId = itemId;
            movement.referenceId = invoiceId;
            movement.movementType = "SALE";
            movement.quantityChange = -quantity;
            movement.costAtTheTime = item.purchasePriceAverage;
            movement.currencyCode = currencyCode;
            movement.date = AppDateTime.formatUtc(timestamp);
            database.inventoryDao().insertMovement(movement);

            List<JournalLine> journalLines = new ArrayList<>();
            if (!onCredit) {
                journalLines.add(journalLine(entryId, cashAccount, null, total, 0));
            } else {
                journalLines.add(journalLine(entryId, receivablesAccount, partnerId, total, 0));
            }
            journalLines.add(journalLine(entryId, salesAccount, null, 0, total));
            if (cost > 0) {
                journalLines.add(journalLine(entryId, cogsAccount, null, cost, 0));
                journalLines.add(journalLine(entryId, inventoryAccount, null, 0, cost));
            }
            new AccountingEngine().postLines(database, journal, journalLines);

            if (!onCredit) {
                Payment payment = new Payment();
                payment.paymentId = UUID.randomUUID().toString();
                payment.idempotencyKey = "sale-payment:" + idempotencyKey;
                payment.direction = "IN";
                payment.currencyCode = currencyCode;
                payment.invoiceId = invoiceId;
                payment.cashBoxId = cashBox.cashBoxId;
                payment.journalEntryId = entryId;
                payment.amount = total;
                payment.createdAt = timestamp;
                database.erpDao().insertPayment(payment);

                CashMovement cashMovement = new CashMovement();
                cashMovement.movementId = UUID.randomUUID().toString();
                cashMovement.cashBoxId = cashBox.cashBoxId;
                cashMovement.referenceType = "SALE";
                cashMovement.referenceId = invoiceId;
                cashMovement.amount = total;
                cashMovement.createdAt = timestamp;
                database.erpDao().insertCashMovement(cashMovement);
            }

            AuditEvent audit = new AuditEvent();
            audit.eventId = UUID.randomUUID().toString();
            audit.userId = actor.userId;
            audit.action = "POST";
            audit.entityType = "INVOICE";
            audit.entityId = invoiceId;
            audit.details = "اعتماد بيع " + item.itemName + "، الكمية " + quantity;
            audit.createdAt = timestamp;
            database.erpDao().insertAuditEvent(audit);
            result[0] = invoice;
        });
        return result[0];
    }

    private void ensureAccount(String id, String name, String type, String currencyCode) {
        if (database.accountDao().getAccount(id) != null) {
            return;
        }
        Account account = new Account();
        account.accountId = id;
        account.accountName = name + " (" + currencyCode + ")";
        account.accountType = type;
        database.accountDao().insertAccountIfMissing(account);
    }

    private static String accountId(String baseId, String currencyCode) {
        return MoneyCurrency.accountId(baseId, currencyCode);
    }

    private static JournalLine journalLine(String entryId, String accountId, String partnerId,
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
}
