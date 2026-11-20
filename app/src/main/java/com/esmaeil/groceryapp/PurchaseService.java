package com.esmaeil.groceryapp;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PurchaseService {
    private static final String PAYABLES = "system-payables";
    private static final String INVENTORY = "system-inventory";
    private static final String CASH = "system-cash";
    private static final String ROUNDING = "system-inventory-rounding";

    private final AppDatabase database;

    public PurchaseService(AppDatabase database) {
        this.database = database;
    }

    public Invoice postPurchase(String supplierId, List<PurchaseLine> lines,
                                long directCosts, long paid, String currencyCode,
                                String idempotencyKey) {
        AppUser actor = SessionManager.require(database, SessionManager.Permission.POST_PURCHASE);
        MoneyCurrency.requireSupported(currencyCode);
        if (supplierId == null || supplierId.trim().isEmpty()
                || lines == null || lines.isEmpty() || directCosts < 0 || paid < 0
                || idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("المورد والأصناف والمبالغ ومفتاح العملية مطلوبة");
        }
        String fingerprint = fingerprint(supplierId, lines, directCosts, paid,
                currencyCode);
        Invoice[] result = new Invoice[1];
        database.runInTransaction(() -> {
            Invoice previous = database.erpDao().getInvoiceByIdempotencyKey(idempotencyKey);
            if (previous != null) {
                if (!fingerprint.equals(previous.requestFingerprint)) {
                    throw new IllegalStateException("مفتاح العملية مستخدم لطلب شراء مختلف");
                }
                result[0] = previous;
                return;
            }

            BusinessPartner supplier = database.erpDao().getPartner(supplierId);
            if (supplier == null || !supplier.isActive
                    || !BusinessPartner.TYPE_SUPPLIER.equals(supplier.type)) {
                throw new IllegalArgumentException("المورد غير موجود أو غير نشط");
            }

            long subtotal = 0;
            long[] lineTotals = new long[lines.size()];
            for (int i = 0; i < lines.size(); i++) {
                PurchaseLine line = lines.get(i);
                if (line == null || line.itemId == null || line.itemId.trim().isEmpty()
                        || line.quantity <= 0 || line.unitPrice <= 0) {
                    throw new IllegalArgumentException("كل صنف يحتاج كمية وسعر شراء موجبين");
                }
                lineTotals[i] = FinancialMath.multiply(line.quantity, line.unitPrice);
                subtotal = FinancialMath.add(subtotal, lineTotals[i]);
            }
            long total = FinancialMath.add(subtotal, directCosts);
            if (total <= 0 || paid > total) {
                throw new IllegalArgumentException("المدفوع يجب ألا يتجاوز إجمالي الفاتورة");
            }
            long timestamp = System.currentTimeMillis();
            if (paid > 0 && database.erpDao().getCashBalance(currencyCode) < paid) {
                throw new IllegalStateException("رصيد الصندوق غير كافٍ للسداد");
            }
            long[] costShares = allocateDirectCosts(lineTotals, subtotal, directCosts);
            String invoiceId = UUID.randomUUID().toString();
            CashBox cashBox = paid == 0 ? null
                    : AccountingAccounts.cashBox(database, currencyCode, timestamp);

            long inventoryChange = 0;
            List<InvoiceLine> invoiceLines = new ArrayList<>();
            List<StockMovement> movements = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                PurchaseLine requestLine = lines.get(i);
                InventoryItem item = database.inventoryDao().getItem(requestLine.itemId);
                if (item == null || !item.isActive) {
                    throw new IllegalArgumentException("صنف شراء غير موجود أو غير نشط");
                }
                if (!currencyCode.equals(item.currencyCode)) {
                    throw new IllegalArgumentException("عملة الصنف لا تطابق عملة فاتورة الشراء");
                }
                long oldValue = FinancialMath.multiply(
                        item.quantity, item.purchasePriceAverage);
                long newQuantity = FinancialMath.add(item.quantity, requestLine.quantity);
                long lineLandedCost = FinancialMath.add(lineTotals[i], costShares[i]);
                long landedUnitCost = roundHalfUp(lineLandedCost, requestLine.quantity);
                long newAverage = InventoryEngine.calculateNewWeightedAverage(item.quantity,
                        item.purchasePriceAverage, requestLine.quantity, landedUnitCost, 0);
                long newValue = FinancialMath.multiply(newQuantity, newAverage);
                long lineInventoryChange = newValue - oldValue;
                if (lineInventoryChange < 0) {
                    throw new IllegalStateException("نتجت قيمة مخزون سالبة؛ أُلغيت الفاتورة");
                }
                inventoryChange = FinancialMath.add(inventoryChange, lineInventoryChange);
                if (database.inventoryDao().updateStockAndAverageCost(
                        item.itemId, newQuantity, newAverage) != 1) {
                    throw new IllegalStateException("تعذر تحديث المخزون؛ أُلغيت الفاتورة");
                }

                InvoiceLine invoiceLine = new InvoiceLine();
                invoiceLine.lineId = UUID.randomUUID().toString();
                invoiceLine.invoiceId = invoiceId;
                invoiceLine.itemId = item.itemId;
                invoiceLine.quantity = requestLine.quantity;
                invoiceLine.unitPrice = requestLine.unitPrice;
                invoiceLine.transportPerUnit = roundHalfUp(
                        costShares[i], requestLine.quantity);
                invoiceLine.costAtPosting = landedUnitCost;
                invoiceLine.lineTotal = lineLandedCost;
                invoiceLines.add(invoiceLine);

                StockMovement movement = new StockMovement();
                movement.movementId = UUID.randomUUID().toString();
                movement.itemId = item.itemId;
                movement.referenceId = invoiceId;
                movement.movementType = "PURCHASE";
                movement.quantityChange = requestLine.quantity;
                movement.costAtTheTime = newAverage;
                movement.currencyCode = currencyCode;
                movement.date = AppDateTime.formatUtc(timestamp);
                movements.add(movement);
            }

            JournalEntry entry = AccountingAccounts.newEntry(database,
                    "ترحيل فاتورة شراء " + invoiceId, "PURCHASE", invoiceId, total);
            Invoice invoice = new Invoice();
            invoice.invoiceId = invoiceId;
            invoice.idempotencyKey = idempotencyKey;
            invoice.requestFingerprint = fingerprint;
            invoice.type = "PURCHASE";
            invoice.partnerId = supplierId;
            invoice.cashBoxId = cashBox == null ? null : cashBox.cashBoxId;
            invoice.issuedAt = timestamp;
            invoice.currencyCode = currencyCode;
            invoice.subtotal = subtotal;
            invoice.directCosts = directCosts;
            invoice.total = total;
            invoice.paid = paid;
            invoice.journalEntryId = entry.entryId;
            invoice.paymentStatus = paymentStatus(paid, total);
            invoice.status = "POSTED";
            database.erpDao().insertInvoice(invoice);
            database.erpDao().insertInvoiceLines(invoiceLines);
            for (StockMovement movement : movements) {
                database.inventoryDao().insertMovement(movement);
            }

            String inventoryAccount = AccountingAccounts.account(
                    database, INVENTORY, "المخزون", "ASSET", currencyCode);
            String payableAccount = AccountingAccounts.account(
                    database, PAYABLES, "ذمم الموردين", "LIABILITY", currencyCode);
            long roundingAdjustment = total - inventoryChange;
            List<JournalLine> journalLines = new ArrayList<>();
            if (inventoryChange > 0) {
                journalLines.add(AccountingAccounts.line(
                        entry.entryId, inventoryAccount, null, inventoryChange, 0));
            }
            if (roundingAdjustment > 0) {
                String roundingAccount = AccountingAccounts.account(
                        database, ROUNDING, "فروق تقريب تكلفة المخزون",
                        "EXPENSE", currencyCode);
                journalLines.add(AccountingAccounts.line(
                        entry.entryId, roundingAccount, null, roundingAdjustment, 0));
            } else if (roundingAdjustment < 0) {
                String roundingAccount = AccountingAccounts.account(
                        database, ROUNDING, "فروق تقريب تكلفة المخزون",
                        "EXPENSE", currencyCode);
                journalLines.add(AccountingAccounts.line(
                        entry.entryId, roundingAccount, null, 0, -roundingAdjustment));
            }
            if (paid > 0) {
                String cashAccount = AccountingAccounts.account(
                        database, CASH, "الصندوق", "ASSET", currencyCode);
                journalLines.add(AccountingAccounts.line(
                        entry.entryId, cashAccount, null, 0, paid));

                Payment payment = new Payment();
                payment.paymentId = UUID.randomUUID().toString();
                payment.idempotencyKey = "purchase-payment:" + idempotencyKey;
                payment.direction = "OUT";
                payment.currencyCode = currencyCode;
                payment.invoiceId = invoiceId;
                payment.partnerId = supplierId;
                payment.cashBoxId = cashBox.cashBoxId;
                payment.journalEntryId = entry.entryId;
                payment.amount = paid;
                payment.createdAt = timestamp;
                database.erpDao().insertPayment(payment);

                CashMovement cashMovement = new CashMovement();
                cashMovement.movementId = UUID.randomUUID().toString();
                cashMovement.cashBoxId = cashBox.cashBoxId;
                cashMovement.referenceType = "PURCHASE";
                cashMovement.referenceId = invoiceId;
                cashMovement.amount = -paid;
                cashMovement.createdAt = timestamp;
                database.erpDao().insertCashMovement(cashMovement);
            }
            long supplierDue = total - paid;
            if (supplierDue > 0) {
                journalLines.add(AccountingAccounts.line(
                        entry.entryId, payableAccount, supplierId, 0, supplierDue));
            }
            AccountingEngine.postLines(database, entry, journalLines);
            AccountingAccounts.audit(database, actor.userId, "POST", "INVOICE",
                    invoiceId, "اعتماد فاتورة شراء " + invoiceId, timestamp);
            result[0] = invoice;
        });
        return result[0];
    }

    private static String paymentStatus(long paid, long total) {
        return paid == 0 ? "UNPAID" : paid == total ? "PAID" : "PARTIAL";
    }

    private static long[] allocateDirectCosts(long[] values, long totalValue, long costs) {
        long[] shares = new long[values.length];
        long allocated = 0;
        BigInteger total = BigInteger.valueOf(totalValue);
        for (int i = 0; i < values.length - 1; i++) {
            BigInteger share = BigInteger.valueOf(costs)
                    .multiply(BigInteger.valueOf(values[i])).divide(total);
            shares[i] = FinancialMath.exactLong(share);
            allocated = FinancialMath.add(allocated, shares[i]);
        }
        shares[values.length - 1] = costs - allocated;
        return shares;
    }

    private static long roundHalfUp(long total, long quantity) {
        BigInteger[] result = BigInteger.valueOf(total)
                .divideAndRemainder(BigInteger.valueOf(quantity));
        if (result[1].shiftLeft(1).compareTo(BigInteger.valueOf(quantity)) >= 0) {
            result[0] = result[0].add(BigInteger.ONE);
        }
        return FinancialMath.exactLong(result[0]);
    }

    private static String fingerprint(String supplierId, List<PurchaseLine> lines,
                                      long directCosts, long paid, String currencyCode) {
        StringBuilder value = new StringBuilder(supplierId).append('|')
                .append(directCosts).append('|').append(paid).append('|').append(currencyCode);
        for (PurchaseLine line : lines) {
            if (line == null) {
                value.append("|null");
            } else {
                value.append('|').append(line.itemId).append(':')
                        .append(line.quantity).append(':').append(line.unitPrice);
            }
        }
        return value.toString();
    }

    public static final class PurchaseLine {
        public final String itemId;
        public final long quantity;
        public final long unitPrice;

        public PurchaseLine(String itemId, long quantity, long unitPrice) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
    }
}
