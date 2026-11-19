package com.esmaeil.groceryapp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public final class LegacyBackupExporter {
    private static final List<String> DATASETS = Arrays.asList(
            "inventory", "sales", "expenses", "debts", "audit_log");

    private LegacyBackupExporter() {}

    public static String exportJson(AppDatabase database) {
        JSONObject backup = new JSONObject();
        try {
            for (String dataset : DATASETS) {
                JSONArray rows = new JSONArray();
                for (LegacyRecord record : database.erpDao().getLegacyRecords(dataset)) {
                    rows.put(new JSONObject(record.payload));
                }
                backup.put(dataset, rows);
            }
            backup.put("version", "2.0.0");
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            backup.put("date", dateFormat.format(new Date()));
            return backup.toString(2);
        } catch (JSONException exception) {
            throw new IllegalStateException("تعذر إنشاء ملف التصدير من الأرشيف المحلي", exception);
        }
    }
}
