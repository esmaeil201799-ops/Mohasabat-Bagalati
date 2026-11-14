package com.esmaeil.groceryapp;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class ReportHelper {

    public static String generateDailySalesReport(String storeName, String totalSales, String cashSales, String creditSales) {
        StringBuilder report = new StringBuilder();
        report.append("===========================\n");
        report.append("     ").append(storeName).append("     \n");
        report.append("    تقرير المبيعات اليومي    \n");
        report.append("===========================\n");
        report.append("إجمالي المبيعات: ").append(totalSales).append("\n");
        report.append("- نقداً (الصندوق): ").append(cashSales).append("\n");
        report.append("- أجل / ديون: ").append(creditSales).append("\n");
        report.append("---------------------------\n");
        report.append("حالة المخزون: مستقر ومحدث\n");
        report.append("الصادر عبر نظام: محاسبة بقالتي 2.0\n");
        report.append("===========================");
        return report.toString();
    }

    public static void shareReport(Context context, String reportText) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "التقرير المالي - محاسبة بقالتي");
            shareIntent.putExtra(Intent.EXTRA_TEXT, reportText);
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة التقرير عبر:"));
        } catch (Exception e) {
            Toast.makeText(context, "خطأ في مشاركة التقرير: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
