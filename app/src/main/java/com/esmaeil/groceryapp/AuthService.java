package com.esmaeil.groceryapp;

import android.os.Build;
import android.util.Base64;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class AuthService {
    private static final int SALT_BYTES = 16;
    private static final int VERIFIER_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 240_000;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MILLIS = 30_000L;

    private final AppDatabase database;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AppDatabase database) {
        this.database = database;
    }

    public String createOwner(String username, char[] pin) {
        String normalizedUsername = normalizeUsername(username);
        validatePin(pin);
        AppUser owner = newUser(normalizedUsername, AppUser.ROLE_OWNER, pin);
        String recoveryCode = createRecoveryCode(owner);
        database.runInTransaction(() -> {
            if (database.erpDao().countAppUsers() != 0) {
                throw new IllegalStateException("تم إعداد مستخدم؛ لا يمكن إنشاء مالك آخر");
            }
            database.erpDao().insertAppUser(owner);
            insertAudit(owner.userId, "OWNER_SETUP", owner.userId,
                    "تهيئة حساب المالك");
        });
        SessionManager.establish(owner.userId);
        return recoveryCode;
    }

    public String createStaffUser(String username, char[] pin, String role) {
        AppUser actor = SessionManager.require(database, SessionManager.Permission.MANAGE_USERS);
        String normalizedUsername = normalizeUsername(username);
        validatePin(pin);
        if (!AppUser.ROLE_MANAGER.equals(role) && !AppUser.ROLE_CASHIER.equals(role)) {
            throw new IllegalArgumentException("الدور المحدد غير مسموح");
        }
        AppUser user = newUser(normalizedUsername, role, pin);
        String recoveryCode = createRecoveryCode(user);
        database.runInTransaction(() -> {
            database.erpDao().insertAppUser(user);
            insertAudit(actor.userId, "USER_CREATE", user.userId, "إنشاء مستخدم بدور " + role);
        });
        return recoveryCode;
    }

    public void login(String username, char[] pin) {
        String normalizedUsername = normalizeUsername(username);
        if (pin == null || pin.length == 0) {
            throw new IllegalArgumentException("اسم المستخدم أو PIN غير صحيح");
        }
        AppUser user = database.erpDao().getAppUserByUsername(normalizedUsername);
        if (user == null || !user.isActive || user.pinSalt == null || user.pinVerifier == null) {
            throw new IllegalArgumentException("اسم المستخدم أو PIN غير صحيح");
        }
        long now = System.currentTimeMillis();
        if (user.pinLockedUntil > now) {
            throw new IllegalStateException("تم إيقاف المحاولات مؤقتًا؛ حاول لاحقًا");
        }
        byte[] candidate = derive(pin, user.pinSalt);
        if (!MessageDigest.isEqual(candidate, user.pinVerifier)) {
            user.failedPinAttempts = Math.min(MAX_FAILED_ATTEMPTS, user.failedPinAttempts + 1);
            if (user.failedPinAttempts >= MAX_FAILED_ATTEMPTS) {
                user.pinLockedUntil = now + LOCKOUT_MILLIS;
            }
            database.runInTransaction(() -> {
                database.erpDao().updateAppUser(user);
                insertAudit(null, "LOGIN_FAILED", user.userId, "محاولة دخول غير ناجحة");
            });
            throw new IllegalArgumentException("اسم المستخدم أو PIN غير صحيح");
        }
        user.failedPinAttempts = 0;
        user.pinLockedUntil = 0;
        database.runInTransaction(() -> {
            database.erpDao().updateAppUser(user);
            insertAudit(user.userId, "LOGIN", user.userId, "تسجيل دخول ناجح");
        });
        SessionManager.establish(user.userId);
    }

    void loginByBiometric(String userId) {
        AppUser user = database.erpDao().getAppUser(userId);
        if (user == null || !user.isActive) {
            throw new IllegalStateException("الحساب غير متاح؛ استخدم PIN");
        }
        insertAudit(user.userId, "BIOMETRIC_LOGIN", user.userId,
                "تسجيل دخول بمصادقة الجهاز");
        SessionManager.establish(user.userId);
    }

    public String recoverPin(String username, String recoveryCode, char[] newPin) {
        String normalizedUsername = normalizeUsername(username);
        validatePin(newPin);
        AppUser user = database.erpDao().getAppUserByUsername(normalizedUsername);
        if (user == null || !user.isActive || user.recoverySalt == null
                || user.recoveryVerifier == null || recoveryCode == null) {
            throw new IllegalArgumentException("بيانات الاستعادة غير صحيحة");
        }
        byte[] candidate = derive(recoveryCode.trim().toCharArray(), user.recoverySalt);
        if (!MessageDigest.isEqual(candidate, user.recoveryVerifier)) {
            insertAudit(null, "RECOVERY_FAILED", user.userId, "محاولة استعادة غير ناجحة");
            throw new IllegalArgumentException("بيانات الاستعادة غير صحيحة");
        }

        byte[] pinSalt = randomBytes(SALT_BYTES);
        user.pinSalt = pinSalt;
        user.pinVerifier = derive(newPin, pinSalt);
        user.failedPinAttempts = 0;
        user.pinLockedUntil = 0;
        String nextRecoveryCode = createRecoveryCode(user);
        database.runInTransaction(() -> {
            database.erpDao().updateAppUser(user);
            insertAudit(user.userId, "PIN_RECOVERY", user.userId,
                    "استعادة PIN وتدوير رمز الاستعادة");
        });
        SessionManager.establish(user.userId);
        return nextRecoveryCode;
    }

    private AppUser newUser(String username, String role, char[] pin) {
        AppUser user = new AppUser();
        user.userId = UUID.randomUUID().toString();
        user.username = username;
        user.role = role;
        user.createdAt = System.currentTimeMillis();
        user.pinSalt = randomBytes(SALT_BYTES);
        user.pinVerifier = derive(pin, user.pinSalt);
        return user;
    }

    private String createRecoveryCode(AppUser user) {
        String code = Base64.encodeToString(randomBytes(18),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        user.recoverySalt = randomBytes(SALT_BYTES);
        user.recoveryVerifier = derive(code.toCharArray(), user.recoverySalt);
        return code;
    }

    private byte[] randomBytes(int count) {
        byte[] value = new byte[count];
        secureRandom.nextBytes(value);
        return value;
    }

    private static byte[] derive(char[] secret, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(secret, salt, PBKDF2_ITERATIONS, VERIFIER_BITS);
        try {
            String algorithm = Build.VERSION.SDK_INT >= 26
                    ? "PBKDF2WithHmacSHA256" : "PBKDF2WithHmacSHA1";
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("تعذر اشتقاق بيانات اعتماد آمنة", exception);
        } finally {
            spec.clearPassword();
        }
    }

    private static String normalizeUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException("اسم المستخدم مطلوب");
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw new IllegalArgumentException("اسم المستخدم غير صالح");
        }
        return normalized;
    }

    private static void validatePin(char[] pin) {
        if (pin == null || pin.length < 6 || pin.length > 12) {
            throw new IllegalArgumentException("يجب أن يتكون PIN من 6 إلى 12 رقمًا");
        }
        for (char digit : pin) {
            if (digit < '0' || digit > '9') {
                throw new IllegalArgumentException("يجب أن يتكون PIN من أرقام فقط");
            }
        }
    }

    private void insertAudit(String userId, String action, String entityId, String details) {
        AuditEvent event = new AuditEvent();
        event.eventId = UUID.randomUUID().toString();
        event.userId = userId;
        event.action = action;
        event.entityType = "AUTH";
        event.entityId = entityId == null ? "" : entityId;
        event.details = details;
        event.createdAt = System.currentTimeMillis();
        database.erpDao().insertAuditEvent(event);
    }
}
