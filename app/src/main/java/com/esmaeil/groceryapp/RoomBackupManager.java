package com.esmaeil.groceryapp;

import android.content.Context;
import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class RoomBackupManager {
    private static final byte[] MAGIC = new byte[]{'M', 'B', 'G', 'B', 'A', 'K', '0', '1'};
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int HASH_BYTES = 32;
    private static final int KEY_BYTES = 32;
    private static final int GCM_TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final long MAX_ARCHIVE_BYTES = 256L * 1024 * 1024;
    private static final long MAX_METADATA_BYTES = 64 * 1024;
    private static final String DATABASE_ENTRY = "database.sqlite";
    private static final String METADATA_ENTRY = "metadata.json";
    private static final List<String> TABLES = Arrays.asList(
            "accounts", "journal_entries", "journal_lines", "inventory_items", "units",
            "stock_movements", "unit_conversions", "business_partners", "invoices",
            "invoice_lines", "payments", "cash_boxes", "cash_movements", "expenses",
            "owner_withdrawals", "app_users", "audit_events", "app_settings", "work_shifts",
            "legacy_archive");

    private RoomBackupManager() {}

    public static BackupResult createEncryptedBackup(Context context, AppDatabase database,
                                                      OutputStream destination, char[] password)
            throws IOException {
        requirePassword(password, false);
        File zipFile = new File(context.getCacheDir(),
                "room-backup-" + UUID.randomUUID() + ".zip");
        byte[] key = null;
        try {
            String databaseName = database.getOpenHelper().getDatabaseName();
            SupportSQLiteDatabase sqlite = database.getOpenHelper().getWritableDatabase();
            Map<String, Long> counts = tableCounts(sqlite);
            long debitTotal = scalar(sqlite,
                    "SELECT COALESCE(SUM(totalDebit), 0) FROM journal_entries");
            long creditTotal = scalar(sqlite,
                    "SELECT COALESCE(SUM(totalCredit), 0) FROM journal_entries");
            if (debitTotal != creditTotal) {
                throw new IOException("قاعدة البيانات تحتوي قيودًا غير متوازنة؛ أُوقف النسخ");
            }
            long yerCash = database.erpDao().getCashBalance(MoneyCurrency.YER);
            long sarCash = database.erpDao().getCashBalance(MoneyCurrency.SAR);
            checkpointAndClose(database);

            File sourceDatabase = context.getDatabasePath(databaseName);
            if (!sourceDatabase.isFile() || sourceDatabase.length() == 0) {
                throw new IOException("ملف قاعدة Room غير موجود أو فارغ");
            }
            String databaseHash = sha256(sourceDatabase);
            JSONObject metadata = new JSONObject();
            metadata.put("createdAt", System.currentTimeMillis());
            metadata.put("createdAtUtc", utcTimestamp(System.currentTimeMillis()));
            metadata.put("appVersion", BuildConfig.VERSION_NAME);
            metadata.put("databaseVersion", AppDatabase.SCHEMA_VERSION);
            metadata.put("databaseSha256", databaseHash);
            metadata.put("journalDebitTotal", debitTotal);
            metadata.put("journalCreditTotal", creditTotal);
            metadata.put("cashYER", yerCash);
            metadata.put("cashSAR", sarCash);
            JSONObject tableCounts = new JSONObject();
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                tableCounts.put(entry.getKey(), entry.getValue());
            }
            metadata.put("tableCounts", tableCounts);
            writeZip(zipFile, sourceDatabase, metadata.toString().getBytes(StandardCharsets.UTF_8));

            byte[] zipHash = sha256Bytes(zipFile);
            byte[] salt = randomBytes(SALT_BYTES);
            byte[] nonce = randomBytes(NONCE_BYTES);
            byte[] header = envelopeHeader(salt, nonce, zipHash);
            key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(header);
            MessageDigest archiveDigest = newSha256();
            try (DataOutputStream output = new DataOutputStream(
                    new java.security.DigestOutputStream(destination, archiveDigest));
                 FileInputStream input = new FileInputStream(zipFile);
                 CipherOutputStream encrypted = new CipherOutputStream(output, cipher)) {
                output.write(header);
                copy(input, encrypted, MAX_ARCHIVE_BYTES);
            }
            return new BackupResult(metadata.getLong("createdAt"), databaseHash,
                    hex(archiveDigest.digest()));
        } catch (JSONException | GeneralSecurityException exception) {
            throw new IOException("تعذر إنشاء النسخة المشفرة", exception);
        } finally {
            Arrays.fill(password, '\0');
            if (key != null) {
                Arrays.fill(key, (byte) 0);
            }
            if (zipFile.exists() && !zipFile.delete()) {
                throw new IOException("تعذر تنظيف ملف النسخ المؤقت");
            }
        }
    }

    public static RestoreCandidate inspectEncryptedBackup(Context context, InputStream source,
                                                          char[] password) throws IOException {
        requirePassword(password, true);
        File zipFile = new File(context.getCacheDir(),
                "room-restore-" + UUID.randomUUID() + ".zip");
        File databaseFile = null;
        byte[] key = null;
        try {
            DataInputStream input = new DataInputStream(source);
            byte[] magic = new byte[MAGIC.length];
            byte[] salt = new byte[SALT_BYTES];
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] expectedZipHash = new byte[HASH_BYTES];
            input.readFully(magic);
            input.readFully(salt);
            input.readFully(nonce);
            input.readFully(expectedZipHash);
            if (!Arrays.equals(MAGIC, magic)) {
                throw new IOException("صيغة ملف النسخة غير مدعومة");
            }
            byte[] header = envelopeHeader(salt, nonce, expectedZipHash);
            key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(header);
            try (DataInputStream encryptedInput = input;
                 FileOutputStream output = new FileOutputStream(zipFile)) {
                decryptToFile(encryptedInput, output, cipher);
            }
            if (!MessageDigest.isEqual(expectedZipHash, sha256Bytes(zipFile))) {
                throw new IOException("فشل التحقق من SHA-256 للأرشيف");
            }

            JSONObject metadata;
            try (ZipFile zip = new ZipFile(zipFile)) {
                if (zip.size() != 2) {
                    throw new IOException("الأرشيف يحتوي مدخلات غير متوقعة");
                }
                ZipEntry databaseEntry = zip.getEntry(DATABASE_ENTRY);
                ZipEntry metadataEntry = zip.getEntry(METADATA_ENTRY);
                if (databaseEntry == null || metadataEntry == null
                        || databaseEntry.isDirectory() || metadataEntry.isDirectory()
                        || databaseEntry.getSize() <= 0
                        || databaseEntry.getSize() > MAX_ARCHIVE_BYTES) {
                    throw new IOException("محتويات الأرشيف غير مكتملة أو غير صالحة");
                }
                metadata = readMetadata(zip.getInputStream(metadataEntry));
                String tempName = "restore-" + UUID.randomUUID() + ".db";
                databaseFile = context.getDatabasePath(tempName);
                try (InputStream dbInput = zip.getInputStream(databaseEntry);
                     FileOutputStream dbOutput = new FileOutputStream(databaseFile)) {
                    copy(dbInput, dbOutput, MAX_ARCHIVE_BYTES);
                }
                if (!sha256(databaseFile).equals(metadata.getString("databaseSha256"))) {
                    throw new IOException("فشل التحقق من SHA-256 لقاعدة البيانات");
                }
                RestoreCandidate candidate = validateCandidate(
                        context, tempName, databaseFile, metadata);
                databaseFile = null;
                return candidate;
            }
        } catch (GeneralSecurityException | JSONException exception) {
            throw new IOException("تعذر فك تشفير النسخة أو قراءة بياناتها", exception);
        } finally {
            Arrays.fill(password, '\0');
            if (key != null) {
                Arrays.fill(key, (byte) 0);
            }
            deleteSpecificFile(zipFile);
            if (databaseFile != null) {
                deleteDatabaseFiles(databaseFile);
            }
        }
    }

    public static RestoreResult restore(Context context, AppDatabase currentDatabase,
                                        RestoreCandidate candidate) throws IOException {
        if (candidate == null || !candidate.databaseFile.isFile()) {
            throw new IOException("ملف النسخة المؤقت غير موجود");
        }
        checkpointAndClose(currentDatabase);
        String databaseName = currentDatabase.getOpenHelper().getDatabaseName();
        File currentFile = context.getDatabasePath(databaseName);
        File preservedFile = new File(currentFile.getPath()
                + ".before-restore-" + UUID.randomUUID());
        File candidateFile = candidate.databaseFile;
        ArrayList<FileMove> moves = new ArrayList<>();
        boolean installed = false;
        try {
            moveIfPresent(currentFile, preservedFile, moves);
            moveIfPresent(sidecar(currentFile, "-wal"), sidecar(preservedFile, "-wal"),
                    moves);
            moveIfPresent(sidecar(currentFile, "-shm"), sidecar(preservedFile, "-shm"),
                    moves);
            if (!candidateFile.renameTo(currentFile)) {
                rollbackMoves(moves);
                throw new IOException("تعذر تثبيت النسخة؛ بقيت قاعدة البيانات الأصلية محفوظة");
            }
            installed = true;
            AppDatabase restored = AppDatabase.open(context, databaseName);
            try {
                validateLiveDatabase(restored, candidate.metadata);
                candidate.markConsumed();
                return new RestoreResult(restored, preservedFile);
            } catch (IOException | RuntimeException | JSONException exception) {
                restored.close();
                File failedFile = new File(currentFile.getPath()
                        + ".failed-restore-" + UUID.randomUUID());
                if (!currentFile.renameTo(failedFile)) {
                    throw new IOException("فشل التحقق وتعذر نقل النسخة المستعادة؛ "
                            + "القاعدة الأصلية محفوظة في " + preservedFile.getName(), exception);
                }
                installed = false;
                rollbackMoves(moves);
                throw new IOException("فشل التحقق النهائي؛ أُعيدت القاعدة الأصلية دون حذف "
                        + "النسخة المرفوضة " + failedFile.getName(), exception);
            }
        } catch (IOException | RuntimeException exception) {
            if (installed && currentFile.exists()) {
                File failedFile = new File(currentFile.getPath()
                        + ".failed-restore-" + UUID.randomUUID());
                if (!currentFile.renameTo(failedFile)) {
                    throw new IOException("تعذر نقل ملف الاستعادة الفاشل؛ لا تحذف الملفات "
                            + "يدويًا، والقاعدة السابقة محفوظة باسم "
                            + preservedFile.getName(), exception);
                }
            }
            rollbackMoves(moves);
            if (exception instanceof IOException) {
                throw (IOException) exception;
            }
            throw new IOException("فشلت الاستعادة؛ أُبقيت قاعدة البيانات الأصلية", exception);
        }
    }

    private static RestoreCandidate validateCandidate(Context context, String databaseName,
                                                       File file, JSONObject metadata)
            throws IOException {
        try {
            if (metadata.getInt("databaseVersion") != AppDatabase.SCHEMA_VERSION) {
                deleteDatabaseFiles(file);
                throw new IOException("إصدار قاعدة النسخة لا يطابق الإصدار المدعوم؛ "
                        + "لم تُغيّر قاعدة التطبيق");
            }
        } catch (JSONException exception) {
            deleteDatabaseFiles(file);
            throw new IOException("إصدار قاعدة البيانات غير موجود في بيان النسخة", exception);
        }
        AppDatabase candidateDatabase = AppDatabase.open(context, databaseName);
        try {
            validateLiveDatabase(candidateDatabase, metadata);
            return new RestoreCandidate(databaseName, file, metadata);
        } catch (RuntimeException | IOException | JSONException exception) {
            candidateDatabase.close();
            deleteDatabaseFiles(file);
            if (exception instanceof IOException) {
                throw (IOException) exception;
            }
            throw new IOException("تعذر فحص النسخة في قاعدة مؤقتة", exception);
        } finally {
            if (candidateDatabase.isOpen()) {
                candidateDatabase.close();
            }
        }
    }

    private static void validateLiveDatabase(AppDatabase database, JSONObject metadata)
            throws IOException, JSONException {
        SupportSQLiteDatabase sqlite = database.getOpenHelper().getWritableDatabase();
        Cursor integrity = sqlite.query("PRAGMA integrity_check");
        try {
            if (!integrity.moveToFirst() || !"ok".equalsIgnoreCase(integrity.getString(0))) {
                throw new IOException("فشل فحص سلامة SQLite");
            }
        } finally {
            integrity.close();
        }
        Cursor foreignKeys = sqlite.query("PRAGMA foreign_key_check");
        try {
            if (foreignKeys.moveToFirst()) {
                throw new IOException("تحتوي النسخة مراجع خارجية غير سليمة");
            }
        } finally {
            foreignKeys.close();
        }

        JSONObject expectedCounts = metadata.getJSONObject("tableCounts");
        Map<String, Long> actualCounts = tableCounts(sqlite);
        for (String table : TABLES) {
            if (expectedCounts.getLong(table) != actualCounts.get(table)) {
                throw new IOException("عدد سجلات الجدول " + table
                        + " لا يطابق بيان النسخة");
            }
        }
        long debit = scalar(sqlite,
                "SELECT COALESCE(SUM(totalDebit), 0) FROM journal_entries");
        long credit = scalar(sqlite,
                "SELECT COALESCE(SUM(totalCredit), 0) FROM journal_entries");
        if (debit != credit || debit != metadata.getLong("journalDebitTotal")
                || credit != metadata.getLong("journalCreditTotal")) {
            throw new IOException("إجماليات القيود لا تتطابق أو غير متوازنة");
        }
        if (database.erpDao().getCashBalance(MoneyCurrency.YER)
                != metadata.getLong("cashYER")
                || database.erpDao().getCashBalance(MoneyCurrency.SAR)
                != metadata.getLong("cashSAR")) {
            throw new IOException("أرصدة الصندوق لا تطابق بيانات النسخة");
        }
    }

    private static Map<String, Long> tableCounts(SupportSQLiteDatabase database)
            throws IOException {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        for (String table : TABLES) {
            Cursor cursor = database.query("SELECT COUNT(*) FROM `" + table + "`");
            try {
                if (!cursor.moveToFirst()) {
                    throw new IOException("تعذر قراءة عدد سجلات " + table);
                }
                counts.put(table, cursor.getLong(0));
            } finally {
                cursor.close();
            }
        }
        return counts;
    }

    private static long scalar(SupportSQLiteDatabase database, String sql) throws IOException {
        Cursor cursor = database.query(sql);
        try {
            if (!cursor.moveToFirst()) {
                throw new IOException("تعذر حساب إجماليات القاعدة");
            }
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    private static void checkpointAndClose(AppDatabase database) throws IOException {
        Cursor checkpoint = database.getOpenHelper().getWritableDatabase()
                .query("PRAGMA wal_checkpoint(FULL)");
        try {
            if (checkpoint.moveToFirst() && (checkpoint.getInt(0) != 0
                    || checkpoint.getInt(1) != checkpoint.getInt(2))) {
                throw new IOException("قاعدة البيانات مشغولة؛ أُوقف أخذ النسخة");
            }
        } finally {
            checkpoint.close();
        }
        database.close();
    }

    private static void writeZip(File destination, File database, byte[] metadata)
            throws IOException {
        try (FileOutputStream fileOutput = new FileOutputStream(destination);
             ZipOutputStream zip = new ZipOutputStream(fileOutput)) {
            zip.putNextEntry(new ZipEntry(METADATA_ENTRY));
            zip.write(metadata);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(DATABASE_ENTRY));
            try (FileInputStream input = new FileInputStream(database)) {
                copy(input, zip, MAX_ARCHIVE_BYTES);
            }
            zip.closeEntry();
        }
    }

    private static JSONObject readMetadata(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(source, output, MAX_METADATA_BYTES);
            return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        } catch (JSONException exception) {
            throw new IOException("بيانات النسخة الوصفية غير صالحة", exception);
        }
    }

    private static byte[] deriveKey(char[] password, byte[] salt)
            throws GeneralSecurityException, IOException {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        byte[] passwordBytes = new byte[encoded.remaining()];
        encoded.get(passwordBytes);
        Arrays.fill(encoded.array(), (byte) 0);
        byte[] block = Arrays.copyOf(salt, salt.length + 4);
        block[block.length - 1] = 1;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(passwordBytes, "HmacSHA256"));
        byte[] u = mac.doFinal(block);
        byte[] result = u.clone();
        try {
            for (int i = 1; i < PBKDF2_ITERATIONS; i++) {
                byte[] next = mac.doFinal(u);
                Arrays.fill(u, (byte) 0);
                u = next;
                for (int j = 0; j < result.length; j++) {
                    result[j] ^= u[j];
                }
            }
            return result;
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
            Arrays.fill(block, (byte) 0);
            Arrays.fill(u, (byte) 0);
        }
    }

    private static byte[] envelopeHeader(byte[] salt, byte[] nonce, byte[] hash) {
        ByteBuffer buffer = ByteBuffer.allocate(
                MAGIC.length + salt.length + nonce.length + hash.length);
        buffer.put(MAGIC).put(salt).put(nonce).put(hash);
        return buffer.array();
    }

    private static byte[] randomBytes(int size) {
        byte[] result = new byte[size];
        new SecureRandom().nextBytes(result);
        return result;
    }

    private static String sha256(File file) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private static byte[] sha256Bytes(File file) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 غير متاح", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        }
        return value.toString();
    }

    private static String utcTimestamp(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(timestamp));
    }

    private static void requirePassword(char[] password, boolean restore) throws IOException {
        if (password == null || password.length < MIN_PASSWORD_LENGTH) {
            throw new IOException(restore
                    ? "كلمة المرور يجب ألا تقل عن 12 حرفًا"
                    : "اختر كلمة مرور قوية من 12 حرفًا على الأقل");
        }
    }

    private static void copy(InputStream input, OutputStream output, long maxBytes)
            throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("حجم الأرشيف يتجاوز الحد المسموح");
            }
            output.write(buffer, 0, read);
        }
    }

    private static void decryptToFile(InputStream input, OutputStream output, Cipher cipher)
            throws IOException, GeneralSecurityException {
        byte[] buffer = new byte[16 * 1024];
        try {
            long encryptedBytes = 0;
            long plaintextBytes = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                encryptedBytes += read;
                if (encryptedBytes > MAX_ARCHIVE_BYTES + GCM_TAG_BITS / 8) {
                    throw new IOException("حجم الأرشيف يتجاوز الحد المسموح");
                }
                byte[] plaintext = cipher.update(buffer, 0, read);
                if (plaintext != null) {
                    try {
                        plaintextBytes += plaintext.length;
                        if (plaintextBytes > MAX_ARCHIVE_BYTES) {
                            throw new IOException("حجم الأرشيف يتجاوز الحد المسموح");
                        }
                        output.write(plaintext);
                    } finally {
                        Arrays.fill(plaintext, (byte) 0);
                    }
                }
            }

            byte[] finalPlaintext;
            try {
                finalPlaintext = cipher.doFinal();
            } catch (GeneralSecurityException exception) {
                throw new IOException("كلمة المرور غير صحيحة أو الأرشيف تالف", exception);
            }
            try {
                plaintextBytes += finalPlaintext.length;
                if (plaintextBytes > MAX_ARCHIVE_BYTES) {
                    throw new IOException("حجم الأرشيف يتجاوز الحد المسموح");
                }
                output.write(finalPlaintext);
            } finally {
                Arrays.fill(finalPlaintext, (byte) 0);
            }
        } finally {
            Arrays.fill(buffer, (byte) 0);
        }
    }

    private static File sidecar(File database, String suffix) {
        return new File(database.getPath() + suffix);
    }

    private static void moveIfPresent(File source, File destination, List<FileMove> moves)
            throws IOException {
        if (!source.exists()) {
            return;
        }
        if (destination.exists() || !source.renameTo(destination)) {
            throw new IOException("تعذر حفظ الملف السابق " + source.getName());
        }
        moves.add(new FileMove(source, destination));
    }

    private static void rollbackMoves(List<FileMove> moves) throws IOException {
        IOException failure = null;
        for (int i = moves.size() - 1; i >= 0; i--) {
            FileMove move = moves.get(i);
            if (move.destination.exists() && !move.destination.renameTo(move.source)) {
                failure = new IOException("تعذر استعادة ملف القاعدة السابق "
                        + move.destination.getName());
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void deleteSpecificFile(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("تعذر تنظيف ملف مؤقت " + file.getName());
        }
    }

    private static void deleteDatabaseFiles(File file) {
        file.delete();
        sidecar(file, "-wal").delete();
        sidecar(file, "-shm").delete();
    }

    public static final class BackupResult {
        public final long createdAt;
        public final String databaseSha256;
        public final String archiveSha256;

        private BackupResult(long createdAt, String databaseSha256, String archiveSha256) {
            this.createdAt = createdAt;
            this.databaseSha256 = databaseSha256;
            this.archiveSha256 = archiveSha256;
        }
    }

    public static final class RestoreCandidate implements AutoCloseable {
        public final long createdAt;
        public final int databaseVersion;
        public final String appVersion;
        public final Map<String, Long> tableCounts;
        private final File databaseFile;
        private final JSONObject metadata;
        private boolean consumed;

        private RestoreCandidate(String databaseName, File databaseFile, JSONObject metadata)
                throws JSONException {
            this.createdAt = metadata.getLong("createdAt");
            this.databaseVersion = metadata.getInt("databaseVersion");
            this.appVersion = metadata.getString("appVersion");
            this.databaseFile = databaseFile;
            this.metadata = metadata;
            JSONObject counts = metadata.getJSONObject("tableCounts");
            LinkedHashMap<String, Long> values = new LinkedHashMap<>();
            for (String table : TABLES) {
                values.put(table, counts.getLong(table));
            }
            this.tableCounts = values;
        }

        private void markConsumed() {
            consumed = true;
        }

        @Override
        public void close() {
            if (!consumed) {
                deleteDatabaseFiles(databaseFile);
            }
        }
    }

    public static final class RestoreResult {
        public final AppDatabase database;
        public final File preservedDatabase;

        private RestoreResult(AppDatabase database, File preservedDatabase) {
            this.database = database;
            this.preservedDatabase = preservedDatabase;
        }
    }

    private static final class FileMove {
        final File source;
        final File destination;

        FileMove(File source, File destination) {
            this.source = source;
            this.destination = destination;
        }
    }
}
