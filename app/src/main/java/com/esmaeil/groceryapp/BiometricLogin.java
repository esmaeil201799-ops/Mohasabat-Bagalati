package com.esmaeil.groceryapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import java.security.UnrecoverableKeyException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class BiometricLogin {
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "com.esmaeil.groceryapp.biometric-login";
    private static final String PREFERENCES = "biometric_login";
    private static final String CIPHERTEXT = "user_id_ciphertext";
    private static final String IV = "user_id_iv";

    private BiometricLogin() {}

    static boolean hasCredential(Context context) {
        return preferences(context).contains(CIPHERTEXT)
                && preferences(context).contains(IV);
    }

    static Cipher createEncryptCipher() {
        try {
            KeyStore keyStore = loadKeyStore();
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator generator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
                KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(true);
                if (Build.VERSION.SDK_INT >= 24) {
                    builder.setInvalidatedByBiometricEnrollment(true);
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    builder.setUserAuthenticationParameters(0,
                            KeyProperties.AUTH_BIOMETRIC_STRONG);
                } else {
                    builder.setUserAuthenticationValidityDurationSeconds(0);
                }
                generator.init(builder.build());
                generator.generateKey();
                keyStore = loadKeyStore();
            }
            SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher;
        } catch (Exception exception) {
            throw new IllegalStateException("تعذر تهيئة حماية البصمة في Android Keystore", exception);
        }
    }

    static Cipher createDecryptCipher(Context context) {
        try {
            byte[] iv = read(context, IV);
            if (iv == null) {
                throw new IllegalStateException("لا يوجد تسجيل دخول بالبصمة");
            }
            KeyStore keyStore = loadKeyStore();
            SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            if (key == null) {
                throw new UnrecoverableKeyException("مفتاح البصمة غير موجود");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return cipher;
        } catch (Exception exception) {
            clear(context);
            throw new IllegalStateException("تعذر فتح بيانات البصمة؛ استخدم PIN", exception);
        }
    }

    static void storeEncryptedUser(Context context, Cipher cipher, String userId) {
        try {
            byte[] encrypted = cipher.doFinal(userId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            boolean stored = preferences(context).edit()
                    .putString(CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .commit();
            if (!stored) {
                clear(context);
                throw new IllegalStateException("تعذر حفظ إعداد البصمة");
            }
        } catch (Exception exception) {
            clear(context);
            throw new IllegalStateException("تعذر حماية الحساب بالبصمة", exception);
        }
    }

    static String decryptUserId(Context context, Cipher cipher) {
        try {
            byte[] ciphertext = read(context, CIPHERTEXT);
            if (ciphertext == null) {
                throw new IllegalStateException("لا يوجد حساب مهيأ للبصمة");
            }
            return new String(cipher.doFinal(ciphertext),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            clear(context);
            throw new IllegalStateException("تعذر التحقق بالبصمة؛ استخدم PIN", exception);
        }
    }

    static void clear(Context context) {
        preferences(context).edit().remove(CIPHERTEXT).remove(IV).commit();
        try {
            KeyStore keyStore = loadKeyStore();
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("تعذر حذف مفتاح البصمة", exception);
        }
    }

    private static KeyStore loadKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        return keyStore;
    }

    private static byte[] read(Context context, String key) {
        String encoded = preferences(context).getString(key, null);
        return encoded == null ? null : Base64.decode(encoded, Base64.NO_WRAP);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
