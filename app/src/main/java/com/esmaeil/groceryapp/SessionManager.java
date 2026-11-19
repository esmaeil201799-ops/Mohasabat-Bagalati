package com.esmaeil.groceryapp;

import android.os.SystemClock;

final class SessionManager {
    enum Permission {
        POST_SALE,
        MANAGE_INVENTORY,
        MANAGE_PARTNERS,
        POST_PURCHASE,
        RECEIVE_PAYMENT,
        PAY_SUPPLIER,
        POST_EXPENSE,
        POST_WITHDRAWAL,
        POST_RETURN,
        VIEW_FINANCIAL_REPORTS,
        MANAGE_USERS,
        MANAGE_BACKUPS
    }

    private static final long SESSION_TIMEOUT_MILLIS = 10 * 60 * 1000L;
    private static String authenticatedUserId;
    private static long lastActivityAt;

    private SessionManager() {}

    static synchronized void establish(String userId) {
        authenticatedUserId = userId;
        lastActivityAt = SystemClock.elapsedRealtime();
    }

    static synchronized void clear() {
        authenticatedUserId = null;
        lastActivityAt = 0;
    }

    static synchronized AppUser currentUser(AppDatabase database) {
        if (authenticatedUserId == null
                || SystemClock.elapsedRealtime() - lastActivityAt > SESSION_TIMEOUT_MILLIS) {
            clear();
            return null;
        }
        AppUser user = database.erpDao().getAppUser(authenticatedUserId);
        if (user == null || !user.isActive) {
            clear();
            return null;
        }
        lastActivityAt = SystemClock.elapsedRealtime();
        return user;
    }

    static AppUser require(AppDatabase database, Permission permission) {
        AppUser user = currentUser(database);
        if (user == null) {
            throw new SecurityException("انتهت الجلسة؛ سجّل الدخول مجددًا");
        }
        if (!isAllowed(user.role, permission)) {
            throw new SecurityException("لا تملك صلاحية تنفيذ هذه العملية");
        }
        return user;
    }

    static synchronized boolean hasSession() {
        if (authenticatedUserId == null
                || SystemClock.elapsedRealtime() - lastActivityAt > SESSION_TIMEOUT_MILLIS) {
            clear();
            return false;
        }
        return true;
    }

    static synchronized String authenticatedUserId() {
        return hasSession() ? authenticatedUserId : null;
    }

    private static boolean isAllowed(String role, Permission permission) {
        if (AppUser.ROLE_OWNER.equals(role)) {
            return true;
        }
        if (AppUser.ROLE_MANAGER.equals(role)) {
            return permission != Permission.MANAGE_USERS
                    && permission != Permission.MANAGE_BACKUPS
                    && permission != Permission.POST_WITHDRAWAL;
        }
        return AppUser.ROLE_CASHIER.equals(role)
                && (permission == Permission.POST_SALE
                || permission == Permission.RECEIVE_PAYMENT);
    }
}
