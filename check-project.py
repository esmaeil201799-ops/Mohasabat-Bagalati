#!/usr/bin/env python3
from pathlib import Path
import re, sys, zipfile
root = Path(__file__).resolve().parent
html = root / 'app/src/main/assets/index.html'
assert html.is_file(), 'index.html missing'
s = html.read_text(encoding='utf-8')
assert 'محاسبة بقالتي' in s
assert 'function exportBackup()' in s, 'JSON backup export function missing'
assert 'function importBackup(event)' in s, 'JSON backup import function missing'
assert 'function mergeBackupRecords(existing, incoming)' in s, 'safe legacy merge missing'
for key in ('inventory', 'sales', 'expenses', 'debts', 'audit_log'):
    assert f'{key}: getData(' in s, f'{key} missing from JSON backup'

java_root = root / 'app/src/main/java'
java_sources = '\n'.join(path.read_text(encoding='utf-8') for path in java_root.rglob('*.java'))
database = (java_root / 'com/esmaeil/groceryapp/AppDatabase.java').read_text(encoding='utf-8')
main_activity = (java_root / 'com/esmaeil/groceryapp/MainActivity.java').read_text(encoding='utf-8')
sale_service = (java_root / 'com/esmaeil/groceryapp/SaleService.java').read_text(encoding='utf-8')
assert 'Room.databaseBuilder' in database, 'Room database builder missing'
assert 'AppDatabase.open' in main_activity, 'Main activity is not connected to Room'
assert 'fallbackToDestructiveMigration' not in database, 'destructive migration fallback is forbidden'
assert 'version = 9' in database and 'MIGRATION_8_9' in database, 'Room schema 9 migration missing'
assert 'database.runInTransaction' in sale_service, 'sales are not posted transactionally'
assert 'import android.database.sqlite.SQLiteOpenHelper' not in java_sources
assert 'import android.database.sqlite.SQLiteDatabase' not in java_sources
assert 'android.webkit.WebView' not in java_sources
room_test = (root / 'app/src/test/java/com/esmaeil/groceryapp/RoomPersistenceTest.java')
assert room_test.is_file(), 'Room persistence tests missing'
room_test_source = room_test.read_text(encoding='utf-8')
assert 'inventoryAndPartnersSurviveDatabaseCloseAndReopen' in room_test_source
assert 'migrationFromVersionFourPreservesInventoryAndJournalData' in room_test_source
for test_name in ('migrationFromVersionSixAddsLogicalDeleteWithoutChangingExistingRows',
                  'cashAndCreditSalesPostInvoiceStockMovementAndBalancedJournal',
                  'failedStockUpdateRollsBackInvoiceJournalAndMovements',
                  'legacyBackupImportIsCountedAndIdempotent',
                  'migrationsThroughVersionNinePreserveUserAndAddAccountingFields',
                  'ownerPinIsDerivedAndRecoveryCodeRotatesAfterUse',
                  'managerIsBlockedFromOwnerOnlyBackupOperations',
                  'saleReturnRestocksOnceRefundsCashAndRejectsExcessQuantity',
                  'purchaseReturnReducesStockAndSupplierBalanceWithBalancedJournal',
                  'purchasePaymentsAndCreditReceiptsUpdateBalancesExactlyOnce',
                  'operatingExpensesAndOwnerWithdrawalsRemainDistinct'):
    assert test_name in room_test_source, f'{test_name} test missing'

ids = re.findall(r'\bid=["\']([^"\']+)["\']', s)
dupes = sorted({x for x in ids if ids.count(x) > 1})
if dupes: print('Duplicate HTML ids:', dupes); sys.exit(2)
assert (root / 'app/src/main/AndroidManifest.xml').is_file()
assert (root / '.github/workflows/build-apk.yml').is_file()
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
about = root / 'app/src/main/res/layout/activity_about.xml'
main_layout = (root / 'app/src/main/res/layout/activity_main.xml').read_text(encoding='utf-8')
identity = (root / 'app/src/main/java/com/esmaeil/groceryapp/AppIdentity.java').read_text(encoding='utf-8')
about_activity = (root / 'app/src/main/java/com/esmaeil/groceryapp/AboutActivity.java').read_text(encoding='utf-8')
assert 'android:label="محاسبة بقالتي"' in manifest, 'official Android app label missing'
assert about.is_file(), 'About screen layout missing'
about_layout = about.read_text(encoding='utf-8')
for view_id in ('tvAboutAppName', 'tvAboutOwner', 'tvAboutVersion',
                'tvAboutReleaseDate', 'tvAboutCopyright', 'tvAboutContact'):
    assert f'@+id/{view_id}' in about_layout, f'{view_id} missing from About screen'
assert '© 2026 إسماعيل أبكر. جميع الحقوق محفوظة.' in identity
for identity_field in ('APP_NAME', 'OWNER', 'DESCRIPTION', 'RELEASE_DATE', 'COPYRIGHT'):
    assert f'AppIdentity.{identity_field}' in about_activity, f'{identity_field} not shown in About activity'
assert 'BuildConfig.VERSION_NAME' in about_activity, 'build version is not shown in About activity'
assert 'إسماعيل أبكر' not in main_layout, 'owner identity must not occupy the main screen'
assert 'CONTACT_EMAIL = ""' in identity, 'official email must remain unset until provided by owner'
print('PASS: index.html exists')
print('PASS: app label/source marker present')
print('PASS: legacy HTML backup remains separate from the native Room runtime')
print('PASS: Android runtime uses Room without independent SQLite/WebView storage')
print('PASS: Room restart and version-4 migration tests are present')
print('PASS: Room v9 migrations, transactional sale/returns, rollback, and legacy import tests are present')
print(f'PASS: {len(ids)} HTML IDs, no duplicates')
print('PASS: Arabic app identity, separate About screen, and unset contact email')
print('NOTE: This is a source preflight only; it does not compile Android or validate runtime behavior.')
