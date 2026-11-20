package com.esmaeil.groceryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.test.core.app.ApplicationProvider;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RoomPersistenceTest {
    private static final String TEST_DATABASE = "room-persistence-test.db";
    private static final String MIGRATION_DATABASE = "room-migration-test.db";
    private static final String BACKUP_DATABASE = "room-backup-test.db";
    private Context context;

    @Rule
    public final TestRule runRoomWorkOffMainThread = new TestRule() {
        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    AtomicReference<Throwable> failure = new AtomicReference<>();
                    Thread testThread = new Thread(() -> {
                        try {
                            base.evaluate();
                        } catch (Throwable throwable) {
                            failure.set(throwable);
                        }
                    }, "RoomTest-" + description.getMethodName());
                    testThread.setContextClassLoader(Thread.currentThread().getContextClassLoader());
                    testThread.start();
                    testThread.join();
                    if (failure.get() != null) {
                        throw failure.get();
                    }
                }
            };
        }
    };

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TEST_DATABASE);
        context.deleteDatabase(MIGRATION_DATABASE);
        context.deleteDatabase(BACKUP_DATABASE);
    }

    @After
    public void tearDown() {
        SessionManager.clear();
        context.deleteDatabase(TEST_DATABASE);
        context.deleteDatabase(MIGRATION_DATABASE);
        context.deleteDatabase(BACKUP_DATABASE);
    }

    @Test
    public void inventoryAndPartnersSurviveDatabaseCloseAndReopen() {
        AppDatabase database = openDatabase();
        try {
            InventoryItem item = new InventoryItem();
            item.itemId = "persistence-test-item";
            item.itemName = "Persistence test item";
            item.barcode = "persistence-test-barcode";
            item.quantity = 17;
            item.purchasePriceAverage = 125;
            item.sellingPrice = 175;
            database.inventoryDao().insertItem(item);

            BusinessPartner customer = new BusinessPartner();
            customer.partnerId = "persistence-test-customer";
            customer.name = "Persistence test customer";
            customer.type = BusinessPartner.TYPE_CUSTOMER;
            database.erpDao().insertPartner(customer);

            BusinessPartner supplier = new BusinessPartner();
            supplier.partnerId = "persistence-test-supplier";
            supplier.name = "Persistence test supplier";
            supplier.type = BusinessPartner.TYPE_SUPPLIER;
            database.erpDao().insertPartner(supplier);
        } finally {
            database.close();
        }

        database = openDatabase();
        try {
            InventoryItem storedItem = database.inventoryDao().getItem("persistence-test-item");
            assertNotNull(storedItem);
            assertEquals(17L, storedItem.quantity);
            assertEquals(125L, storedItem.purchasePriceAverage);

            assertEquals("persistence-test-customer",
                    database.erpDao().getCustomers().get(0).partnerId);
            assertEquals("persistence-test-supplier",
                    database.erpDao().getSuppliers().get(0).partnerId);
        } finally {
            database.close();
        }
    }

    @Test
    public void migrationFromVersionFourPreservesInventoryAndJournalData() {
        createVersionFourDatabase();

        AppDatabase database = openDatabase();
        try {
            InventoryItem item = database.inventoryDao().getItem("legacy-item");
            assertNotNull(item);
            assertEquals(31L, item.quantity);

            StockMovement movement = database.inventoryDao().getMovementById("legacy-movement");
            assertNotNull(movement);
            assertEquals(-4L, movement.quantityChange);

            Cursor journalLines = database.getOpenHelper().getReadableDatabase().query(
                    "SELECT entryId, accountId, partnerId, debit, credit "
                            + "FROM journal_lines WHERE lineId = 'legacy-line'");
            try {
                assertTrue(journalLines.moveToFirst());
                assertEquals("legacy-entry", journalLines.getString(0));
                assertEquals("legacy-account", journalLines.getString(1));
                assertNull(journalLines.getString(2));
                assertEquals(31L, journalLines.getLong(3));
                assertEquals(0L, journalLines.getLong(4));
            } finally {
                journalLines.close();
            }
        } finally {
            database.close();
        }
    }

    @Test
    public void encryptedBackupIsVerifiedInTemporaryRoomAndRestorePreservesOldDatabase()
            throws Exception {
        AppDatabase backupDatabase = AppDatabase.open(context, BACKUP_DATABASE);
        insertInventoryItem(backupDatabase, "backup-snapshot-item", 12, 35, 60);
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        RoomBackupManager.BackupResult created = RoomBackupManager.createEncryptedBackup(
                context, backupDatabase, archive, "backup-passphrase-2026".toCharArray());
        assertEquals(64, created.archiveSha256.length());

        AppDatabase current = AppDatabase.open(context, BACKUP_DATABASE);
        insertInventoryItem(current, "newer-device-item", 7, 45, 80);
        byte[] damagedArchive = archive.toByteArray();
        damagedArchive[damagedArchive.length - 1] ^= 1;
        try {
            RoomBackupManager.inspectEncryptedBackup(context,
                    new ByteArrayInputStream(damagedArchive),
                    "backup-passphrase-2026".toCharArray());
            throw new AssertionError("A modified encrypted archive was accepted");
        } catch (IOException expected) {
            assertNotNull(current.inventoryDao().getItem("newer-device-item"));
        }

        RoomBackupManager.RestoreCandidate candidate =
                RoomBackupManager.inspectEncryptedBackup(context,
                        new ByteArrayInputStream(archive.toByteArray()),
                        "backup-passphrase-2026".toCharArray());
        assertEquals(AppDatabase.SCHEMA_VERSION, candidate.databaseVersion);
        assertEquals(Long.valueOf(1), candidate.tableCounts.get("inventory_items"));

        RoomBackupManager.RestoreResult restored =
                RoomBackupManager.restore(context, current, candidate);
        try {
            assertNotNull(restored.database.inventoryDao().getItem("backup-snapshot-item"));
            assertNull(restored.database.inventoryDao().getItem("newer-device-item"));
            assertTrue(restored.preservedDatabase.isFile());

            String preservedName = restored.preservedDatabase.getName();
            AppDatabase preserved = AppDatabase.open(context, preservedName);
            try {
                assertNotNull(preserved.inventoryDao().getItem("backup-snapshot-item"));
                assertNotNull(preserved.inventoryDao().getItem("newer-device-item"));
            } finally {
                preserved.close();
            }
            assertTrue(context.deleteDatabase(preservedName));
            new File(restored.preservedDatabase.getPath() + "-wal").delete();
            new File(restored.preservedDatabase.getPath() + "-shm").delete();
        } finally {
            restored.database.close();
            candidate.close();
        }
    }

    @Test
    public void migrationFromVersionSixAddsLogicalDeleteWithoutChangingExistingRows()
            throws IOException {
        createVersionSixDatabase();
        SQLiteDatabase versionSix = SQLiteDatabase.openDatabase(
                context.getDatabasePath(MIGRATION_DATABASE).getPath(), null,
                SQLiteDatabase.OPEN_READWRITE);
        versionSix.execSQL("INSERT INTO inventory_items "
                + "(itemId, itemName, barcode, category, baseUnitId, quantity, "
                + "purchasePriceAverage, sellingPrice, minStockAlert, expiryDate) "
                + "VALUES ('v6-item', 'Version six item', NULL, 'General', NULL, 9, 15, 20, 0, NULL)");
        versionSix.close();

        AppDatabase database = AppDatabase.open(context, MIGRATION_DATABASE);
        try {
            InventoryItem retained = database.inventoryDao().getItem("v6-item");
            assertNotNull(retained);
            assertTrue(retained.isActive);
            assertEquals(9L, retained.quantity);
            assertEquals(0, database.erpDao().countLegacyRecords("inventory"));
        } finally {
            database.close();
        }
    }

    @Test
    public void migrationsThroughVersionNinePreserveUserAndAddAccountingFields()
            throws IOException {
        createVersionSixDatabase();
        SQLiteDatabase versionSix = SQLiteDatabase.openDatabase(
                context.getDatabasePath(MIGRATION_DATABASE).getPath(), null,
                SQLiteDatabase.OPEN_READWRITE);
        versionSix.execSQL("INSERT INTO app_users "
                + "(userId, username, role, pinSalt, pinVerifier, isActive, createdAt) "
                + "VALUES ('legacy-user', 'legacy', 'OWNER', X'01', X'02', 1, 123)");
        versionSix.close();

        AppDatabase database = AppDatabase.open(context, MIGRATION_DATABASE);
        SupportSQLiteDatabase migrated = database.getOpenHelper().getWritableDatabase();

        Cursor user = migrated.query("SELECT userId, username, failedPinAttempts, "
                + "pinLockedUntil, recoverySalt FROM app_users WHERE userId = 'legacy-user'");
        try {
            assertTrue(user.moveToFirst());
            assertEquals("legacy-user", user.getString(0));
            assertEquals("legacy", user.getString(1));
            assertEquals(0, user.getInt(2));
            assertEquals(0L, user.getLong(3));
            assertNull(user.getBlob(4));
        } finally {
            user.close();
        }
        Cursor invoiceColumns = migrated.query("PRAGMA table_info(invoices)");
        boolean hasCurrency = false;
        boolean hasReturns = false;
        try {
            while (invoiceColumns.moveToNext()) {
                String column = invoiceColumns.getString(1);
                hasCurrency |= "currencyCode".equals(column);
                hasReturns |= "returnedAmount".equals(column);
            }
        } finally {
            invoiceColumns.close();
            database.close();
        }
        assertTrue(hasCurrency);
        assertTrue(hasReturns);
    }

    @Test
    public void ownerPinIsDerivedAndRecoveryCodeRotatesAfterUse() {
        AppDatabase database = openDatabase();
        try {
            AuthService auth = new AuthService(database);
            String firstRecoveryCode = auth.createOwner("Owner", "123456".toCharArray());
            AppUser owner = database.erpDao().getAppUserByUsername("owner");
            assertNotNull(owner);
            assertFalse(java.util.Arrays.equals("123456".getBytes(), owner.pinVerifier));
            assertEquals(32, owner.pinVerifier.length);

            SessionManager.clear();
            try {
                auth.login("owner", "000000".toCharArray());
                throw new AssertionError("Invalid PIN was accepted");
            } catch (IllegalArgumentException expected) {
                // Expected invalid credentials.
            }
            auth.login("owner", "123456".toCharArray());
            SessionManager.clear();
            String secondRecoveryCode = auth.recoverPin(
                    "owner", firstRecoveryCode, "654321".toCharArray());

            SessionManager.clear();
            try {
                auth.recoverPin("owner", firstRecoveryCode, "111111".toCharArray());
                throw new AssertionError("A consumed recovery code was accepted");
            } catch (IllegalArgumentException expected) {
                // Recovery codes are single-use.
            }
            auth.recoverPin("owner", secondRecoveryCode, "222222".toCharArray());
            SessionManager.clear();
            auth.login("owner", "222222".toCharArray());
        } finally {
            SessionManager.clear();
            database.close();
        }
    }

    @Test
    public void managerIsBlockedFromOwnerOnlyBackupOperations() {
        AppDatabase database = openDatabase();
        try {
            AuthService auth = new AuthService(database);
            auth.createOwner("owner", "123456".toCharArray());
            auth.createStaffUser("manager", "654321".toCharArray(), AppUser.ROLE_MANAGER);
            SessionManager.clear();
            auth.login("manager", "654321".toCharArray());
            boolean denied = false;
            try {
                SessionManager.require(database, SessionManager.Permission.MANAGE_BACKUPS);
            } catch (SecurityException expected) {
                denied = true;
            }
            assertTrue(denied);
        } finally {
            SessionManager.clear();
            database.close();
        }
    }

    @Test
    public void cashAndCreditSalesPostInvoiceStockMovementAndBalancedJournal() {
        AppDatabase database = openDatabase();
        try {
            establishOwner(database);
            insertInventoryItem(database, "cash-sale-item", 4, 50, 100);
            String cashKey = "cash-sale-once";
            Invoice cashInvoice = new SaleService(database).postSale(
                    "cash-sale-item", 2, false, null, cashKey);
            assertEquals("POSTED", cashInvoice.status);
            assertEquals(200L, cashInvoice.total);
            assertEquals(200L, cashInvoice.paid);
            assertEquals(2L, database.inventoryDao().getItem("cash-sale-item").quantity);
            assertNotNull(database.inventoryDao().getMovementByReference(
                    cashInvoice.invoiceId, "SALE"));
            assertEquals(1, countRows(database, "invoices"));
            assertEquals(1, countRows(database, "payments"));
            assertEquals(1, countRows(database, "cash_movements"));
            assertBalancedJournal(database, cashInvoice.journalEntryId);

            Invoice repeated = new SaleService(database).postSale(
                    "cash-sale-item", 2, false, null, cashKey);
            assertEquals(cashInvoice.invoiceId, repeated.invoiceId);
            assertEquals(2L, database.inventoryDao().getItem("cash-sale-item").quantity);
            assertEquals(1, countRows(database, "invoices"));
            boolean keyConflict = false;
            try {
                new SaleService(database).postSale(
                        "cash-sale-item", 1, false, null, cashKey);
            } catch (IllegalStateException expected) {
                keyConflict = true;
            }
            assertTrue(keyConflict);
            assertEquals(2L, database.inventoryDao().getItem("cash-sale-item").quantity);
            assertEquals(1, countRows(database, "invoices"));

            insertInventoryItem(database, "credit-sale-item", 3, 25, 60);
            Invoice creditInvoice = new SaleService(database).postSale(
                    "credit-sale-item", 1, true, "عميل اختبار", "credit-sale-once");
            assertEquals("SALE_CREDIT", creditInvoice.type);
            assertEquals(0L, creditInvoice.paid);
            assertNotNull(database.erpDao().getPartner(creditInvoice.partnerId));
            assertEquals(2, countRows(database, "invoices"));
            assertEquals(1, countRows(database, "payments"));
            assertEquals(1, countRows(database, "cash_movements"));
            assertBalancedJournal(database, creditInvoice.journalEntryId);
        } finally {
            database.close();
        }
    }

    @Test
    public void saleReturnRestocksOnceRefundsCashAndRejectsExcessQuantity() {
        AppDatabase database = openDatabase();
        try {
            establishOwner(database);
            insertInventoryItem(database, "sale-return-item", 4, 50, 100);
            Invoice sale = new SaleService(database).postSale(
                    "sale-return-item", 2, false, null, "sale-return-source");
            InvoiceLine soldLine = database.erpDao().getInvoiceLines(sale.invoiceId).get(0);

            ReturnService service = new ReturnService(database);
            ReturnService.ReturnLine line =
                    new ReturnService.ReturnLine(soldLine.lineId, 1, true);
            Invoice returned = service.returnSale(
                    sale.invoiceId, Collections.singletonList(line), "sale-return-once");

            assertEquals("SALE_RETURN", returned.type);
            assertEquals(sale.invoiceId, returned.originalInvoiceId);
            assertEquals(100L, returned.total);
            assertEquals(3L, database.inventoryDao().getItem("sale-return-item").quantity);
            assertEquals(50L, database.inventoryDao()
                    .getItem("sale-return-item").purchasePriceAverage);
            assertEquals(100L, database.erpDao().getCashBalance(MoneyCurrency.YER));
            assertBalancedJournal(database, returned.journalEntryId);
            assertEquals(returned.invoiceId, service.returnSale(
                    sale.invoiceId, Collections.singletonList(line), "sale-return-once")
                    .invoiceId);

            boolean excessRejected = false;
            try {
                service.returnSale(sale.invoiceId, Collections.singletonList(
                        new ReturnService.ReturnLine(soldLine.lineId, 2, true)),
                        "sale-return-excess");
            } catch (IllegalArgumentException expected) {
                excessRejected = true;
            }
            assertTrue(excessRejected);
            assertEquals(3L, database.inventoryDao().getItem("sale-return-item").quantity);
            assertEquals(2, countRows(database, "invoices"));
        } finally {
            database.close();
        }
    }

    @Test
    public void purchaseReturnReducesStockAndSupplierBalanceWithBalancedJournal() {
        AppDatabase database = openDatabase();
        try {
            establishOwner(database);
            insertInventoryItem(database, "purchase-return-item", 0, 0, 100);
            BusinessPartner supplier = new BusinessPartner();
            supplier.partnerId = "purchase-return-supplier";
            supplier.name = "Purchase return supplier";
            supplier.type = BusinessPartner.TYPE_SUPPLIER;
            supplier.createdAt = System.currentTimeMillis();
            database.erpDao().insertPartner(supplier);

            Invoice purchase = new PurchaseService(database).postPurchase(
                    supplier.partnerId,
                    Collections.singletonList(new PurchaseService.PurchaseLine(
                            "purchase-return-item", 2, 50)),
                    20, 0, MoneyCurrency.YER, "purchase-return-source");
            InvoiceLine purchasedLine = database.erpDao()
                    .getInvoiceLines(purchase.invoiceId).get(0);

            Invoice returned = new ReturnService(database).returnPurchase(
                    purchase.invoiceId,
                    Collections.singletonList(new ReturnService.ReturnLine(
                            purchasedLine.lineId, 1, false)),
                    "purchase-return-once");

            assertEquals("PURCHASE_RETURN", returned.type);
            assertEquals(60L, returned.total);
            assertEquals(1L, database.inventoryDao()
                    .getItem("purchase-return-item").quantity);
            assertEquals(60L, database.inventoryDao()
                    .getItem("purchase-return-item").purchasePriceAverage);
            assertEquals(60L, database.erpDao().getInvoice(purchase.invoiceId).returnedAmount);
            assertBalancedJournal(database, returned.journalEntryId);
        } finally {
            database.close();
        }
    }

    @Test
    public void purchasePaymentsAndCreditReceiptsUpdateBalancesExactlyOnce() {
        AppDatabase database = openDatabase();
        try {
            establishOwner(database);
            new CashOpeningService(database).postOpeningCash(
                    1_000, MoneyCurrency.YER, "service-opening-cash");
            insertInventoryItem(database, "purchase-payment-item", 0, 0, 100);
            BusinessPartner supplier = new BusinessPartner();
            supplier.partnerId = "payment-service-supplier";
            supplier.name = "Payment service supplier";
            supplier.type = BusinessPartner.TYPE_SUPPLIER;
            supplier.createdAt = System.currentTimeMillis();
            database.erpDao().insertPartner(supplier);

            Invoice purchase = new PurchaseService(database).postPurchase(
                    supplier.partnerId,
                    Collections.singletonList(new PurchaseService.PurchaseLine(
                            "purchase-payment-item", 2, 50)),
                    20, 30, MoneyCurrency.YER, "purchase-payment-source");
            assertEquals(120L, purchase.total);
            assertEquals(970L, database.erpDao().getCashBalance(MoneyCurrency.YER));
            assertEquals(60L, database.inventoryDao()
                    .getItem("purchase-payment-item").purchasePriceAverage);
            assertBalancedJournal(database, purchase.journalEntryId);

            Payment supplierPayment = new PaymentService(database).postSupplierPayment(
                    purchase.invoiceId, 50, "supplier-payment-once");
            assertEquals(supplierPayment.paymentId,
                    new PaymentService(database).postSupplierPayment(
                            purchase.invoiceId, 50, "supplier-payment-once").paymentId);
            assertEquals(80L, database.erpDao().getInvoice(purchase.invoiceId).paid);
            assertEquals(920L, database.erpDao().getCashBalance(MoneyCurrency.YER));
            assertBalancedJournal(database, supplierPayment.journalEntryId);

            Invoice creditSale = new SaleService(database).postSale(
                    "purchase-payment-item", 1, true, "عميل التحصيل", "credit-payment-source");
            Payment receipt = new PaymentService(database).postCustomerReceipt(
                    creditSale.invoiceId, 25, "customer-receipt-once");
            assertEquals(25L, database.erpDao().getInvoice(creditSale.invoiceId).paid);
            assertEquals(945L, database.erpDao().getCashBalance(MoneyCurrency.YER));
            assertBalancedJournal(database, receipt.journalEntryId);
        } finally {
            database.close();
        }
    }

    @Test
    public void multiItemPartialSaleUpdatesEachStockCashCustomerAndJournalOnce() {
        AppDatabase database = openDatabase();
        try {
            establishOwner(database);
            insertInventoryItem(database, "multi-sale-first", 5, 20, 50);
            insertInventoryItem(database, "multi-sale-second", 4, 40, 100);
            List<SaleService.SaleLine> lines = Arrays.asList(
                    new SaleService.SaleLine("multi-sale-first", 2),
                    new SaleService.SaleLine("multi-sale-second", 1));

            SaleService service = new SaleService(database);
            Invoice sale = service.postSale(lines, 70, "عميل بيع جزئي",
                    MoneyCurrency.YER, "multi-sale-partial");

            assertEquals("SALE_CREDIT", sale.type);
            assertEquals("PARTIAL", sale.paymentStatus);
            assertEquals(200L, sale.total);
            assertEquals(70L, sale.paid);
            assertNotNull(database.erpDao().getPartner(sale.partnerId));
            assertEquals(3L, database.inventoryDao().getItem("multi-sale-first").quantity);
            assertEquals(3L, database.inventoryDao().getItem("multi-sale-second").quantity);
            assertEquals(2, database.erpDao().getInvoiceLines(sale.invoiceId).size());
            assertEquals(70L, database.erpDao().getCashBalance(MoneyCurrency.YER));
            assertEquals(1, countRows(database, "payments"));
            assertBalancedJournal(database, sale.journalEntryId);

            Invoice repeated = service.postSale(lines, 70, "عميل بيع جزئي",
                    MoneyCurrency.YER, "multi-sale-partial");
            assertEquals(sale.invoiceId, repeated.invoiceId);
            assertEquals(3L, database.inventoryDao().getItem("multi-sale-first").quantity);
            assertEquals(1, countRows(database, "invoices"));
            assertEquals(1, countRows(database, "payments"));
        } finally {
            database.close();
        }
    }

    @Test
    public void openingBalancesAndNewItemsPostBalancedEntriesAndSettleNormally() {
        AppDatabase database = openDatabase();
        try {
            establishOwner(database);
            new CashOpeningService(database).postOpeningCash(
                    300, MoneyCurrency.YER, "opening-full-cash");

            InventoryItem item = new InventoryItem();
            item.itemId = "opening-created-item";
            item.itemName = "Opening created item";
            item.sellingPrice = 80;
            new InventoryService(database).createItem(
                    item, 3, 20, "opening-created-item-once");
            assertEquals(3L, database.inventoryDao().getItem(item.itemId).quantity);
            assertEquals(20L, database.inventoryDao().getItem(item.itemId).purchasePriceAverage);
            new InventoryService(database).createItem(
                    item, 3, 20, "opening-created-item-once");
            assertEquals(1, countRows(database, "inventory_items"));
            assertEquals(1, countRows(database, "stock_movements"));

            BusinessPartner customer = createPartner(database,
                    "opening-customer", BusinessPartner.TYPE_CUSTOMER);
            BusinessPartner supplier = createPartner(database,
                    "opening-supplier", BusinessPartner.TYPE_SUPPLIER);
            OpeningBalanceService openingBalances = new OpeningBalanceService(database);
            Invoice receivable = openingBalances.postPartnerBalance(customer.partnerId,
                    OpeningBalanceService.CUSTOMER_RECEIVABLE, 150,
                    MoneyCurrency.YER, "opening-customer-balance");
            Invoice payable = openingBalances.postPartnerBalance(supplier.partnerId,
                    OpeningBalanceService.SUPPLIER_PAYABLE, 200,
                    MoneyCurrency.YER, "opening-supplier-balance");
            assertEquals(1, database.erpDao().getOutstandingInvoicesByTypes(
                    Collections.singletonList(OpeningBalanceService.CUSTOMER_RECEIVABLE)).size());
            assertEquals(1, database.erpDao().getOutstandingInvoicesByTypes(
                    Collections.singletonList(OpeningBalanceService.SUPPLIER_PAYABLE)).size());
            assertBalancedJournal(database, receivable.journalEntryId);
            assertBalancedJournal(database, payable.journalEntryId);

            Payment receipt = new PaymentService(database).postCustomerReceipt(
                    receivable.invoiceId, 50, "opening-customer-receipt");
            Payment payment = new PaymentService(database).postSupplierPayment(
                    payable.invoiceId, 80, "opening-supplier-payment");
            assertEquals(100L, database.erpDao().getInvoice(receivable.invoiceId).total
                    - database.erpDao().getInvoice(receivable.invoiceId).paid);
            assertEquals(120L, database.erpDao().getInvoice(payable.invoiceId).total
                    - database.erpDao().getInvoice(payable.invoiceId).paid);
            assertEquals(270L, database.erpDao().getCashBalance(MoneyCurrency.YER));
            assertBalancedJournal(database, receipt.journalEntryId);
            assertBalancedJournal(database, payment.journalEntryId);
        } finally {
            database.close();
        }
    }

    @Test
    public void supplierCreditPurchaseDoesNotRequireOpeningCash() {
        AppDatabase database = openDatabase();
        try {
            establishOwner(database);
            insertInventoryItem(database, "credit-only-purchase-item", 0, 0, 100);
            BusinessPartner supplier = createPartner(database,
                    "credit-only-supplier", BusinessPartner.TYPE_SUPPLIER);

            Invoice purchase = new PurchaseService(database).postPurchase(
                    supplier.partnerId,
                    Collections.singletonList(new PurchaseService.PurchaseLine(
                            "credit-only-purchase-item", 4, 25)),
                    0, 0, MoneyCurrency.YER, "credit-only-purchase");

            assertEquals(0L, database.erpDao().getCashBalance(MoneyCurrency.YER));
            assertEquals("UNPAID", purchase.paymentStatus);
            assertEquals(4L, database.inventoryDao()
                    .getItem("credit-only-purchase-item").quantity);
            assertBalancedJournal(database, purchase.journalEntryId);
        } finally {
            database.close();
        }
    }

    @Test
    public void operatingExpensesAndOwnerWithdrawalsRemainDistinct() {
        AppDatabase database = openDatabase();
        try {
            establishOwner(database);
            new CashOpeningService(database).postOpeningCash(
                    500, MoneyCurrency.YER, "withdrawal-opening-cash");
            insertInventoryItem(database, "owner-withdrawal-item", 4, 20, 35);

            Expense expense = new ExpenseService(database).postExpense(
                    "إيجار", 50, MoneyCurrency.YER, "expense-once");
            OwnerWithdrawalService withdrawals = new OwnerWithdrawalService(database);
            OwnerWithdrawal cashWithdrawal = withdrawals.withdrawCash(
                    25, MoneyCurrency.YER, "cash-withdrawal-once");
            OwnerWithdrawal inKindWithdrawal = withdrawals.withdrawInventory(
                    "owner-withdrawal-item", 2, "stock-withdrawal-once");

            assertEquals(50L, expense.amount);
            assertEquals(OwnerWithdrawal.KIND_CASH, cashWithdrawal.kind);
            assertEquals(OwnerWithdrawal.KIND_IN_KIND, inKindWithdrawal.kind);
            assertEquals(40L, inKindWithdrawal.amount);
            assertEquals(2L, database.inventoryDao()
                    .getItem("owner-withdrawal-item").quantity);
            assertEquals(425L, database.erpDao().getCashBalance(MoneyCurrency.YER));
            assertEquals(1, countRows(database, "expenses"));
            assertEquals(2, countRows(database, "owner_withdrawals"));
            assertBalancedJournal(database, expense.journalEntryId);
            assertBalancedJournal(database, cashWithdrawal.journalEntryId);
            assertBalancedJournal(database, inKindWithdrawal.journalEntryId);
        } finally {
            database.close();
        }
    }

    @Test
    public void openingStockUsesWeightedCostAndIsIdempotent() {
        AppDatabase database = openDatabase();
        try {
            establishOwner(database);
            insertInventoryItem(database, "opening-stock-item", 3, 100, 150);
            InventoryService service = new InventoryService(database);
            service.postOpeningStock("opening-stock-item", 2, 200, "opening-batch-once");
            InventoryItem received = database.inventoryDao().getItem("opening-stock-item");
            assertEquals(5L, received.quantity);
            assertEquals(140L, received.purchasePriceAverage);

            service.postOpeningStock("opening-stock-item", 2, 200, "opening-batch-once");
            assertEquals(5L, database.inventoryDao().getItem("opening-stock-item").quantity);
            assertEquals(1, countRows(database, "journal_entries"));
            assertEquals(1, countRows(database, "stock_movements"));
            Cursor entry = database.getOpenHelper().getReadableDatabase()
                    .query("SELECT entryId FROM journal_entries");
            try {
                assertTrue(entry.moveToFirst());
                assertBalancedJournal(database, entry.getString(0));
            } finally {
                entry.close();
            }
        } finally {
            database.close();
        }
    }

    @Test
    public void failedStockUpdateRollsBackInvoiceJournalAndMovements() {
        AppDatabase database = openDatabase();
        try {
            establishOwner(database);
            insertInventoryItem(database, "rollback-item", 5, 25, 50);
            database.getOpenHelper().getWritableDatabase().execSQL(
                    "CREATE TRIGGER force_stock_update_to_abort BEFORE UPDATE OF quantity "
                            + "ON inventory_items WHEN OLD.itemId = 'rollback-item' "
                            + "BEGIN SELECT RAISE(IGNORE); END");
            boolean failed = false;
            try {
                new SaleService(database).postSale(
                        "rollback-item", 1, false, null, "rollback-sale");
            } catch (IllegalStateException expected) {
                failed = true;
            }
            assertTrue(failed);
            assertEquals(5L, database.inventoryDao().getItem("rollback-item").quantity);
            assertEquals(0, countRows(database, "invoices"));
            assertEquals(0, countRows(database, "journal_entries"));
            assertEquals(0, countRows(database, "stock_movements"));
            assertEquals(0, countRows(database, "payments"));
            assertEquals(0, countRows(database, "cash_movements"));
        } finally {
            database.close();
        }
    }

    @Test
    public void databaseRejectsUnbalancedJournalAndKeepsItUnposted() {
        AppDatabase database = openDatabase();
        try {
            Account account = new Account();
            account.accountId = "unbalanced-test-account";
            account.accountName = "Test account";
            account.accountType = "ASSET";
            database.accountDao().insertAccount(account);

            JournalEntry entry = new JournalEntry();
            entry.entryId = "unbalanced-test-entry";
            entry.date = AppDateTime.formatUtc(System.currentTimeMillis());
            entry.description = "Unbalanced test";
            entry.referenceType = "TEST";
            entry.referenceId = "unbalanced";
            entry.totalDebit = 10;
            entry.totalCredit = 10;
            entry.isPosted = false;
            database.accountDao().insertJournalEntry(entry);

            JournalLine line = new JournalLine();
            line.lineId = "unbalanced-test-line";
            line.entryId = entry.entryId;
            line.accountId = account.accountId;
            line.debit = 10;
            database.accountDao().insertJournalLines(
                    java.util.Collections.singletonList(line));

            entry.isPosted = true;
            boolean rejected = false;
            try {
                database.accountDao().updateJournalEntry(entry);
            } catch (android.database.sqlite.SQLiteConstraintException expected) {
                rejected = true;
            }
            assertTrue(rejected);
            assertFalse(database.accountDao().getJournalEntry(entry.entryId).isPosted);
        } finally {
            database.close();
        }
    }

    @Test
    public void legacyBackupImportIsCountedAndIdempotent() throws Exception {
        AppDatabase database = openDatabase();
        try {
            String backup = "{\"inventory\":[{\"id\":11,\"name\":\"أرز\",\"qty\":5}],"
                    + "\"sales\":[{\"itemName\":\"أرز\",\"total\":10},"
                    + "{\"itemName\":\"أرز\",\"total\":10}],"
                    + "\"expenses\":[],\"debts\":[],\"audit_log\":[]}";
            LegacyBackupImporter.ImportResult first =
                    LegacyBackupImporter.importJson(database, backup);
            assertEquals(1, first.datasets.get("inventory").importedCount);
            assertEquals(2, first.datasets.get("sales").importedCount);
            assertEquals(1, first.datasets.get("inventory").afterCount);
            assertEquals(2, first.datasets.get("sales").afterCount);

            LegacyBackupImporter.ImportResult second =
                    LegacyBackupImporter.importJson(database, backup);
            assertEquals(0, second.datasets.get("inventory").importedCount);
            assertEquals(1, second.datasets.get("inventory").duplicateCount);
            assertEquals(0, second.datasets.get("sales").importedCount);
            assertEquals(2, second.datasets.get("sales").duplicateCount);
            assertEquals(1, second.datasets.get("inventory").afterCount);
            assertEquals(2, second.datasets.get("sales").afterCount);
            JSONObject exported = new JSONObject(LegacyBackupExporter.exportJson(database));
            assertEquals(1, exported.getJSONArray("inventory").length());
            assertEquals(2, exported.getJSONArray("sales").length());
        } finally {
            database.close();
        }
    }

    private AppDatabase openDatabase() {
        return AppDatabase.open(context, TEST_DATABASE);
    }

    private static void establishOwner(AppDatabase database) {
        AppUser owner = new AppUser();
        owner.userId = "room-test-owner";
        owner.username = "room-test-owner";
        owner.role = AppUser.ROLE_OWNER;
        owner.createdAt = System.currentTimeMillis();
        database.erpDao().insertAppUser(owner);
        SessionManager.establish(owner.userId);
    }

    private static void insertInventoryItem(AppDatabase database, String id, long quantity,
                                            long cost, long sellingPrice) {
        InventoryItem item = new InventoryItem();
        item.itemId = id;
        item.itemName = id;
        item.quantity = quantity;
        item.purchasePriceAverage = cost;
        item.sellingPrice = sellingPrice;
        database.inventoryDao().insertItem(item);
    }

    private static BusinessPartner createPartner(AppDatabase database, String id, String type) {
        BusinessPartner partner = new BusinessPartner();
        partner.partnerId = id;
        partner.name = id;
        partner.type = type;
        partner.createdAt = System.currentTimeMillis();
        database.erpDao().insertPartner(partner);
        return partner;
    }

    private static int countRows(AppDatabase database, String table) {
        Cursor cursor = database.getOpenHelper().getReadableDatabase()
                .query("SELECT COUNT(*) FROM " + table);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    private static void assertBalancedJournal(AppDatabase database, String entryId) {
        Cursor cursor = database.getOpenHelper().getReadableDatabase().query(
                "SELECT totalDebit, totalCredit, isPosted FROM journal_entries WHERE entryId = '"
                        + entryId + "'");
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals(cursor.getLong(0), cursor.getLong(1));
            assertEquals(1, cursor.getInt(2));
            Cursor lines = database.getOpenHelper().getReadableDatabase().query(
                    "SELECT COALESCE(SUM(debit), 0), COALESCE(SUM(credit), 0) "
                            + "FROM journal_lines WHERE entryId = '" + entryId + "'");
            try {
                assertTrue(lines.moveToFirst());
                assertEquals(cursor.getLong(0), lines.getLong(0));
                assertEquals(cursor.getLong(1), lines.getLong(1));
            } finally {
                lines.close();
            }
        } finally {
            cursor.close();
        }
    }

    private void createVersionFourDatabase() {
        SQLiteOpenHelper legacyDatabase = new SQLiteOpenHelper(context, TEST_DATABASE, null, 4) {
            @Override
            public void onCreate(SQLiteDatabase database) {
                database.execSQL("CREATE TABLE accounts (accountId TEXT NOT NULL PRIMARY KEY, "
                        + "accountName TEXT, accountType TEXT, parentAccountId TEXT, "
                        + "balance INTEGER NOT NULL)");
                database.execSQL("CREATE TABLE journal_entries (entryId TEXT NOT NULL PRIMARY KEY, "
                        + "date TEXT, description TEXT, referenceType TEXT, referenceId TEXT, "
                        + "totalDebit INTEGER NOT NULL, totalCredit INTEGER NOT NULL, "
                        + "isPosted INTEGER NOT NULL)");
                database.execSQL("CREATE TABLE journal_lines (lineId TEXT NOT NULL PRIMARY KEY, "
                        + "entryId TEXT, accountId TEXT, debit INTEGER NOT NULL, credit INTEGER NOT NULL)");
                database.execSQL("CREATE TABLE inventory_items (itemId TEXT NOT NULL PRIMARY KEY, "
                        + "itemName TEXT, barcode TEXT, category TEXT, baseUnitId TEXT, "
                        + "quantity INTEGER NOT NULL, purchasePriceAverage INTEGER NOT NULL, "
                        + "sellingPrice INTEGER NOT NULL, minStockAlert INTEGER NOT NULL, expiryDate TEXT)");
                database.execSQL("CREATE TABLE units (unitId TEXT NOT NULL PRIMARY KEY, unitName TEXT)");
                database.execSQL("CREATE TABLE stock_movements (movementId TEXT NOT NULL PRIMARY KEY, "
                        + "itemId TEXT, referenceId TEXT, movementType TEXT, "
                        + "quantityChange INTEGER NOT NULL, costAtTheTime INTEGER NOT NULL, date TEXT)");

                database.execSQL("INSERT INTO accounts VALUES "
                        + "('legacy-account', 'Legacy account', 'ASSET', NULL, 31)");
                database.execSQL("INSERT INTO journal_entries VALUES "
                        + "('legacy-entry', '2026-01-01', 'Legacy entry', 'TEST', 'legacy-ref', "
                        + "31, 31, 1)");
                database.execSQL("INSERT INTO journal_lines VALUES "
                        + "('legacy-line', 'legacy-entry', 'legacy-account', 31, 0)");
                database.execSQL("INSERT INTO inventory_items VALUES "
                        + "('legacy-item', 'Legacy item', 'legacy-barcode', 'General', 'unit', "
                        + "31, 12, 20, 2, NULL)");
                database.execSQL("INSERT INTO units VALUES ('unit', 'piece')");
                database.execSQL("INSERT INTO stock_movements VALUES "
                        + "('legacy-movement', 'legacy-item', 'legacy-ref', 'ADJUSTMENT', -4, 12, "
                        + "'2026-01-01')");
            }

            @Override
            public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
                throw new IllegalStateException("Unexpected legacy test database upgrade");
            }
        };
        legacyDatabase.getWritableDatabase();
        legacyDatabase.close();
    }

    private void createVersionSixDatabase() throws IOException {
        SQLiteOpenHelper legacyDatabase = new SQLiteOpenHelper(context, MIGRATION_DATABASE, null, 6) {
            @Override
            public void onCreate(SQLiteDatabase database) {
                try (InputStream schemaStream = RoomPersistenceTest.class.getClassLoader()
                        .getResourceAsStream(
                                "com.esmaeil.groceryapp.AppDatabase/6.json")) {
                    if (schemaStream == null) {
                        throw new IOException("Room version 6 schema resource is missing");
                    }
                    ByteArrayOutputStream schemaBytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = schemaStream.read(buffer)) != -1) {
                        schemaBytes.write(buffer, 0, read);
                    }
                    JSONObject exported = new JSONObject(
                            new String(schemaBytes.toByteArray(), StandardCharsets.UTF_8));
                    JSONObject schema = exported.getJSONObject("database");
                    org.json.JSONArray entities = schema.getJSONArray("entities");
                    for (int index = 0; index < entities.length(); index++) {
                        JSONObject entity = entities.getJSONObject(index);
                        database.execSQL(entity.getString("createSql").replace(
                                "${TABLE_NAME}", entity.getString("tableName")));
                    }
                    for (int index = 0; index < entities.length(); index++) {
                        org.json.JSONArray indices = entities.getJSONObject(index)
                                .optJSONArray("indices");
                        if (indices == null) {
                            continue;
                        }
                        for (int indexInEntity = 0; indexInEntity < indices.length();
                             indexInEntity++) {
                            JSONObject entityIndex = indices.getJSONObject(indexInEntity);
                            database.execSQL(entityIndex.getString("createSql").replace(
                                    "${TABLE_NAME}", entities.getJSONObject(index)
                                            .getString("tableName")));
                        }
                    }
                    org.json.JSONArray setupQueries = schema.getJSONArray("setupQueries");
                    for (int index = 0; index < setupQueries.length(); index++) {
                        database.execSQL(setupQueries.getString(index));
                    }
                } catch (IOException | org.json.JSONException exception) {
                    throw new IllegalStateException("Could not create exported Room v6 schema",
                            exception);
                }
            }

            @Override
            public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
                throw new IllegalStateException("Unexpected legacy test database upgrade");
            }
        };
        legacyDatabase.getWritableDatabase();
        legacyDatabase.close();
    }
}
