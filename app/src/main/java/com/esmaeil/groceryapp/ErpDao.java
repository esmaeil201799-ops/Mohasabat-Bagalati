package com.esmaeil.groceryapp;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ErpDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertPartner(BusinessPartner partner);

    @Update
    int updatePartner(BusinessPartner partner);

    @Query("SELECT * FROM business_partners WHERE type = 'CUSTOMER' AND isActive = 1 "
            + "ORDER BY name COLLATE NOCASE")
    List<BusinessPartner> getActiveCustomers();

    @Query("SELECT * FROM business_partners WHERE type = 'CUSTOMER' AND isActive = 1 "
            + "ORDER BY name COLLATE NOCASE")
    List<BusinessPartner> getCustomers();

    @Query("SELECT * FROM business_partners WHERE type = 'SUPPLIER' AND isActive = 1 "
            + "ORDER BY name COLLATE NOCASE")
    List<BusinessPartner> getSuppliers();

    @Query("SELECT * FROM business_partners WHERE type = 'SUPPLIER' AND isActive = 1 "
            + "AND name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE")
    List<BusinessPartner> searchSuppliers(String query);

    @Query("SELECT * FROM business_partners WHERE type = 'CUSTOMER' AND isActive = 1 "
            + "AND name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE")
    List<BusinessPartner> searchCustomers(String query);

    @Query("SELECT * FROM business_partners WHERE type = :type AND isActive = 1 "
            + "AND name = :name COLLATE NOCASE LIMIT 1")
    BusinessPartner getActivePartnerByName(String type, String name);

    @Query("UPDATE business_partners SET isActive = 0 "
            + "WHERE partnerId = :partnerId AND isActive = 1")
    int softDeletePartner(String partnerId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertCashBox(CashBox cashBox);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertCashBoxIfMissing(CashBox cashBox);

    @Query("SELECT * FROM cash_boxes WHERE cashBoxId = :cashBoxId LIMIT 1")
    CashBox getCashBox(String cashBoxId);

    @Query("SELECT * FROM cash_boxes WHERE currencyCode = :currencyCode "
            + "AND isActive = 1 ORDER BY createdAt LIMIT 1")
    CashBox getActiveCashBoxForCurrency(String currencyCode);

    @Query("SELECT * FROM cash_boxes WHERE isActive = 1 ORDER BY createdAt LIMIT 1")
    CashBox getFirstActiveCashBox();

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertInvoice(Invoice invoice);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertInvoiceLines(List<InvoiceLine> lines);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertPayment(Payment payment);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertCashMovement(CashMovement movement);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertExpense(Expense expense);

    @Query("SELECT * FROM expenses WHERE idempotencyKey = :key LIMIT 1")
    Expense getExpenseByIdempotencyKey(String key);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertOwnerWithdrawal(OwnerWithdrawal withdrawal);

    @Query("SELECT * FROM owner_withdrawals WHERE idempotencyKey = :key LIMIT 1")
    OwnerWithdrawal getOwnerWithdrawalByIdempotencyKey(String key);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertAuditEvent(AuditEvent event);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertAppSetting(AppSetting setting);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertUnitConversion(UnitConversion conversion);

    @Query("SELECT * FROM business_partners WHERE partnerId = :partnerId LIMIT 1")
    BusinessPartner getPartner(String partnerId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertAppUser(AppUser user);

    @Update
    int updateAppUser(AppUser user);

    @Query("SELECT * FROM app_users WHERE username = :username COLLATE NOCASE LIMIT 1")
    AppUser getAppUserByUsername(String username);

    @Query("SELECT * FROM app_users WHERE userId = :userId LIMIT 1")
    AppUser getAppUser(String userId);

    @Query("SELECT COUNT(*) FROM app_users")
    int countAppUsers();

    @Query("SELECT * FROM app_users WHERE isActive = 1 ORDER BY createdAt")
    List<AppUser> getActiveAppUsers();

    @Query("SELECT * FROM invoices WHERE idempotencyKey = :key LIMIT 1")
    Invoice getInvoiceByIdempotencyKey(String key);

    @Query("SELECT * FROM invoices WHERE invoiceId = :invoiceId LIMIT 1")
    Invoice getInvoice(String invoiceId);

    @Update
    int updateInvoice(Invoice invoice);

    @Query("SELECT * FROM invoices WHERE status = 'POSTED' "
            + "AND type IN ('SALE_CASH', 'SALE_CREDIT') "
            + "ORDER BY issuedAt DESC LIMIT 100")
    List<Invoice> getRecentSales();

    @Query("SELECT * FROM invoices WHERE status = 'POSTED' AND type = :type "
            + "ORDER BY issuedAt DESC LIMIT 100")
    List<Invoice> getRecentInvoicesByType(String type);

    @Query("SELECT * FROM invoices WHERE status = 'POSTED' AND type IN (:types) "
            + "ORDER BY issuedAt DESC LIMIT 100")
    List<Invoice> getRecentInvoicesByTypes(List<String> types);

    @Query("SELECT * FROM invoices WHERE status = 'POSTED' AND type IN (:types) "
            + "AND total > paid + returnedAmount ORDER BY issuedAt DESC")
    List<Invoice> getOutstandingInvoicesByTypes(List<String> types);

    @Query("SELECT i.* FROM invoices i LEFT JOIN business_partners p "
            + "ON p.partnerId = i.partnerId WHERE i.status = 'POSTED' AND "
            + "(i.type IN ('SALE_CASH', 'SALE_CREDIT') OR i.type = 'SALE_RETURN') "
            + "AND (i.invoiceId LIKE '%' || :query || '%' OR p.name LIKE '%' || :query || '%') "
            + "ORDER BY i.issuedAt DESC LIMIT 100")
    List<Invoice> searchSales(String query);

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'SALE_RETURN' THEN -total ELSE total END), 0) "
            + "FROM invoices WHERE status = 'POSTED' "
            + "AND type IN ('SALE_CASH', 'SALE_CREDIT', 'SALE_RETURN') "
            + "AND issuedAt >= :start AND issuedAt < :end AND currencyCode = :currencyCode")
    long getSalesTotal(long start, long end, String currencyCode);

    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM cash_movements m "
            + "JOIN cash_boxes b ON b.cashBoxId = m.cashBoxId "
            + "WHERE b.currencyCode = :currencyCode")
    long getCashBalance(String currencyCode);

    @Query("SELECT * FROM cash_movements WHERE referenceType = :referenceType "
            + "AND referenceId = :referenceId LIMIT 1")
    CashMovement getCashMovementByReference(String referenceType, String referenceId);

    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :invoiceId")
    List<InvoiceLine> getInvoiceLines(String invoiceId);

    @Query("SELECT * FROM invoice_lines WHERE lineId = :lineId LIMIT 1")
    InvoiceLine getInvoiceLine(String lineId);

    @Query("SELECT COALESCE(SUM(l.quantity), 0) FROM invoice_lines l "
            + "JOIN invoices i ON i.invoiceId = l.invoiceId "
            + "WHERE l.originalLineId = :originalLineId AND i.status = 'POSTED'")
    long getReturnedQuantity(String originalLineId);

    @Query("SELECT COALESCE(SUM(l.lineTotal), 0) FROM invoice_lines l "
            + "JOIN invoices i ON i.invoiceId = l.invoiceId "
            + "WHERE l.originalLineId = :originalLineId AND i.status = 'POSTED'")
    long getReturnedLineTotal(String originalLineId);

    @Query("SELECT * FROM payments WHERE idempotencyKey = :key LIMIT 1")
    Payment getPaymentByIdempotencyKey(String key);

    @Query("SELECT settingValue FROM app_settings WHERE settingKey = :key LIMIT 1")
    String getSetting(String key);

    @Query("SELECT baseUnitsPerUnit FROM unit_conversions "
            + "WHERE itemId = :itemId AND unitId = :unitId LIMIT 1")
    Long getBaseUnitsPerUnit(String itemId, String unitId);

    @Query("SELECT COUNT(*) FROM legacy_archive WHERE dataset = :dataset")
    int countLegacyRecords(String dataset);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertLegacyRecord(LegacyRecord record);

    @Query("SELECT * FROM legacy_archive WHERE dataset = :dataset ORDER BY recordKey")
    List<LegacyRecord> getLegacyRecords(String dataset);
}
