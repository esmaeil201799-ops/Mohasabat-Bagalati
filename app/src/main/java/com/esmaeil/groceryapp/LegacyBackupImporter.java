package com.esmaeil.groceryapp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public final class LegacyBackupImporter {
    private static final String[] DATASETS = {
            "inventory", "sales", "expenses", "debts", "audit_log"
    };

    private LegacyBackupImporter() {}

    public static ImportResult importJson(AppDatabase database, String json) {
        final JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException exception) {
            throw new IllegalArgumentException("ملف النسخة الاحتياطية ليس JSON صالحًا", exception);
        }

        Map<String, List<LegacyRecord>> recordsByDataset = new LinkedHashMap<>();
        for (String dataset : DATASETS) {
            JSONArray rows = root.optJSONArray(dataset);
            if (rows == null) {
                if (root.has(dataset)) {
                    throw new IllegalArgumentException("بيانات " + dataset + " ليست قائمة صالحة");
                }
                rows = new JSONArray();
            }
            recordsByDataset.put(dataset, createRecords(dataset, rows));
        }

        ImportResult result = new ImportResult();
        long importedAt = System.currentTimeMillis();
        database.runInTransaction(() -> {
            for (Map.Entry<String, List<LegacyRecord>> entry : recordsByDataset.entrySet()) {
                String dataset = entry.getKey();
                DatasetImport count = new DatasetImport();
                count.beforeCount = database.erpDao().countLegacyRecords(dataset);
                for (LegacyRecord record : entry.getValue()) {
                    record.importedAt = importedAt;
                    if (database.erpDao().insertLegacyRecord(record) == -1L) {
                        count.duplicateCount++;
                    } else {
                        count.importedCount++;
                    }
                }
                count.afterCount = database.erpDao().countLegacyRecords(dataset);
                result.datasets.put(dataset, count);
            }
        });
        return result;
    }

    private static List<LegacyRecord> createRecords(String dataset, JSONArray rows) {
        List<LegacyRecord> records = new ArrayList<>();
        Map<String, Integer> occurrences = new HashMap<>();
        try {
            for (int index = 0; index < rows.length(); index++) {
                Object value = rows.get(index);
                if (!(value instanceof JSONObject)) {
                    throw new IllegalArgumentException(
                            "كل سجل في " + dataset + " يجب أن يكون كائن JSON");
                }
                String payload = value.toString();
                String rowHash = sha256(payload);
                Integer previousOccurrences = occurrences.get(rowHash);
                int occurrence = previousOccurrences == null ? 0 : previousOccurrences;
                occurrences.put(rowHash, occurrence + 1);

                String sourceId = ((JSONObject) value).optString("id", "");
                LegacyRecord record = new LegacyRecord();
                record.dataset = dataset;
                record.recordKey = sourceId.isEmpty()
                        ? rowHash + ":" + occurrence : "id:" + sourceId + ":" + rowHash;
                record.payload = payload;
                records.add(record);
            }
        } catch (JSONException exception) {
            throw new IllegalArgumentException("تعذر قراءة سجل من " + dataset, exception);
        }
        return records;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 غير متاح على هذا الجهاز", exception);
        }
    }

    public static final class ImportResult {
        public final Map<String, DatasetImport> datasets = new LinkedHashMap<>();

        public String summary() {
            StringBuilder text = new StringBuilder();
            for (Map.Entry<String, DatasetImport> entry : datasets.entrySet()) {
                DatasetImport count = entry.getValue();
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(entry.getKey()).append(": قبل ")
                        .append(count.beforeCount).append("، أضيف ")
                        .append(count.importedCount).append("، مكرر ")
                        .append(count.duplicateCount).append("، بعد ")
                        .append(count.afterCount);
            }
            return text.toString().trim();
        }
    }

    public static final class DatasetImport {
        public int beforeCount;
        public int importedCount;
        public int duplicateCount;
        public int afterCount;
    }
}
