package com.esmaeil.groceryapp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
        return postSaleInternal(java.util.Collections.singletonList(
                        new SaleLine(itemId, quantity)), 0, customerName, currencyCode,
                idempotencyKey, !onCredit);
    }

    public Invoice postSale(List<SaleLine> lines, long paid, String customerName,
                            String currencyCode, String idempotencyKey) {
        return postSaleInternal(lines, paid, customerName, currencyCode, idempotencyKey, false);
    }

    public Invoice postSaleFullyPaid(List<SaleLine> lines, String currencyCode,
                                     String idempotencyKey) {
        return postSaleInternal(lines, 0, null, currencyCode, idempotencyKey, true);
    }

    private Invoice postSaleInternal(List<SaleLine> lines, long paid, String customerName,
                                     String currencyCode, String idempotencyKey,
                                     boolean payFullAmount) {
        AppUser actor = SessionManager.require(database, SessionManager.Permission.POST_SALE);
        MoneyCurrency.requireSupported(currencyCode);
        if (lines == null || lines.isEmpty() || paid < 0
                || idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("الأصناف والمبلغ ومفتاح العملية مطلوبة");
        }
        List<SaleLine> requestedLines = new ArrayList<>(lines);
        String normalizedCustomer = customerName == null ? "" : customerName.trim();
        String fingerprint = fingerprint(requestedLines, paid, payFullAmount,
                normalizedCustomer, currencyCode);
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

            List<InventoryItem> items = new ArrayList<>();
            Set<String> itemIds = new HashSet<>();
            long total = 0;
            long cost = 0;
            for (SaleLine line : requestedLines) {
                if (line == null || line.itemId == null || line.itemId.trim().isEmpty()
                        || line.quantity <= 0 || !itemIds.add(line.itemId)) {
                    throw new IllegalArgumentException(
                            "كل صنف يجب أن يكون فريدًا وذا كمية موجبة");
                }
                InventoryItem item = database.inventoryDao().getItem(line.itemId);
                if (item == null || !item.isActive
                        || !currencyCode.equals(item.currencyCode)) {
                    throw new IllegalArgumentException(
                            "الصنف غير موجود أو عملته لا تطابق عملة البيع");
                }
                if (item.sellingPrice <= 0 || item.purchasePriceAverage < 0
                        || item.quantity < line.quantity) {
                    throw new IllegalStateException(
                            "السعر غير صالح أو الكمية المتاحة غير كافية للصنف " + item.itemName);
                }
                items.add(item);
                total = FinancialMath.add(total,
                        FinancialMath.multiply(item.sellingPrice, line.quantity));
                cost = FinancialMath.add(cost,
                        FinancialMath.multiply(item.purchasePriceAverage, line.quantity));
            }

            long amountPaid = payFullAmount ? total : paid;
            if (amountPaid > total) {
                throw new IllegalArgumentException("المبلغ المدفوع يتجاوز إجمالي البيع");
            }
            long due = total - amountPaid;
            if (due > 0 && normalizedCustomer.isEmpty()) {
                throw new IllegalArgumentException("اسم العميل مطلوب عند وجود مبلغ آجل");
            }

            long timestamp = System.currentTimeMillis();
            String invoiceId = UUID.randomUUID().toString();
            String partnerId = null;
            if (due > 0) {
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

            CashBox cashBox = amountPaid == 0 ? null
                    : AccountingAccounts.cashBox(database, currencyCode, timestamp);
            String cashAccount = AccountingAccounts.account(database, CASH_ACCOUNT,
                    "الصندوق", "ASSET", currencyCode);
            String receivablesAccount = AccountingAccounts.account(database, RECEIVABLES_ACCOUNT,
                    "ذمم العملاء", "ASSET", currencyCode);
            String salesAccount = AccountingAccounts.account(database, SALES_ACCOUNT,
                    "إيرادات المبيعات", "INCOME", currencyCode);
            String cogsAccount = AccountingAccounts.account(database, COGS_ACCOUNT,
                    "تكلفة البضاعة المباعة", "EXPENSE", currencyCode);
            String inventoryAccount = AccountingAccounts.account(database, INVENTORY_ACCOUNT,
                    "المخزون", "ASSET", currencyCode);

            long debitTotal = FinancialMath.add(total, cost);
            JournalEntry journal = AccountingAccounts.newEntry(database,
                    "ترحيل فاتورة بيع " + invoiceId, "SALE", invoiceId, debitTotal);
            Invoice invoice = new Invoice();
            invoice.invoiceId = invoiceId;
            invoice.idempotencyKey = idempotencyKey;
            invoice.requestFingerprint = fingerprint;
            invoice.type = due == 0 ? "SALE_CASH" : "SALE_CREDIT";
            invoice.partnerId = partnerId;
            invoice.cashBoxId = cashBox == null ? null : cashBox.cashBoxId;
            invoice.journalEntryId = journal.entryId;
            invoice.currencyCode = currencyCode;
            invoice.issuedAt = timestamp;
            invoice.subtotal = total;
            invoice.total = total;
            invoice.paid = amountPaid;
            invoice.paymentStatus = due == 0 ? "PAID"
                    : amountPaid == 0 ? "UNPAID" : "PARTIAL";
            invoice.status = "POSTED";
            database.erpDao().insertInvoice(invoice);

            List<InvoiceLine> invoiceLines = new ArrayList<>();
            List<StockMovement> movements = new ArrayList<>();
            for (int i = 0; i < requestedLines.size(); i++) {
                SaleLine request = requestedLines.get(i);
                InventoryItem item = items.get(i);
                InvoiceLine line = new InvoiceLine();
                line.lineId = UUID.randomUUID().toString();
                line.invoiceId = invoiceId;
                line.itemId = item.itemId;
                line.quantity = request.quantity;
                line.unitPrice = item.sellingPrice;
                line.costAtPosting = item.purchasePriceAverage;
                line.lineTotal = FinancialMath.multiply(item.sellingPrice, request.quantity);
                invoiceLines.add(line);

                if (database.inventoryDao().decreaseStock(item.itemId, request.quantity) != 1) {
                    throw new IllegalStateException(
                            "تغير المخزون أثناء البيع؛ لم تُحفظ الفاتورة");
                }
                StockMovement movement = new StockMovement();
                movement.movementId = UUID.randomUUID().toString();
                movement.itemId = item.itemId;
                movement.referenceId = invoiceId;
                movement.movementType = "SALE";
                movement.quantityChange = -request.quantity;
                movement.costAtTheTime = item.purchasePriceAverage;
                movement.currencyCode = currencyCode;
                movement.date = AppDateTime.formatUtc(timestamp);
                movements.add(movement);
            }
            database.erpDao().insertInvoiceLines(invoiceLines);
            for (StockMovement movement : movements) {
                database.inventoryDao().insertMovement(movement);
            }

            List<JournalLine> journalLines = new ArrayList<>();
            if (amountPaid > 0) {
                journalLines.add(AccountingAccounts.line(
                        journal.entryId, cashAccount, null, amountPaid, 0));
            }
            if (due > 0) {
                journalLines.add(AccountingAccounts.line(
                        journal.entryId, receivablesAccount, partnerId, due, 0));
            }
            journalLines.add(AccountingAccounts.line(
                    journal.entryId, salesAccount, null, 0, total));
            if (cost > 0) {
                journalLines.add(AccountingAccounts.line(
                        journal.entryId, cogsAccount, null, cost, 0));
                journalLines.add(AccountingAccounts.line(
                        journal.entryId, inventoryAccount, null, 0, cost));
            }
            AccountingEngine.postLines(database, journal, journalLines);

            if (amountPaid > 0) {
                Payment payment = new Payment();
                payment.paymentId = UUID.randomUUID().toString();
                payment.idempotencyKey = "sale-payment:" + idempotencyKey;
                payment.direction = "IN";
                payment.currencyCode = currencyCode;
                payment.invoiceId = invoiceId;
                payment.cashBoxId = cashBox.cashBoxId;
                payment.journalEntryId = journal.entryId;
                payment.amount = amountPaid;
                payment.createdAt = timestamp;
                database.erpDao().insertPayment(payment);

                CashMovement cashMovement = new CashMovement();
                cashMovement.movementId = UUID.randomUUID().toString();
                cashMovement.cashBoxId = cashBox.cashBoxId;
                cashMovement.referenceType = "SALE";
                cashMovement.referenceId = invoiceId;
                cashMovement.amount = amountPaid;
                cashMovement.createdAt = timestamp;
                database.erpDao().insertCashMovement(cashMovement);
            }

            AccountingAccounts.audit(database, actor.userId, "POST", "INVOICE",
                    invoiceId, "اعتماد بيع متعدد الأصناف، " + requestedLines.size()
                            + " صنفًا وإجمالي " + total, timestamp);
            result[0] = invoice;
        });
        return result[0];
    }

    private static String fingerprint(List<SaleLine> lines, long paid, boolean payFullAmount,
                                      String customerName, String currencyCode) {
        StringBuilder value = new StringBuilder(currencyCode).append('|')
                .append(payFullAmount ? "FULL" : paid).append('|')
                .append(customerName.toLowerCase(Locale.ROOT));
        for (SaleLine line : lines) {
            if (line == null) {
                value.append("|null");
            } else {
                value.append('|').append(line.itemId).append(':').append(line.quantity);
            }
        }
        return value.toString();
    }

    public static final class SaleLine {
        public final String itemId;
        public final long quantity;

        public SaleLine(String itemId, long quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }
}
