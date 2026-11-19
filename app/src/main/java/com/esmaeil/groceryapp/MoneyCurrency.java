package com.esmaeil.groceryapp;

import java.util.Arrays;
import java.util.List;

final class MoneyCurrency {
    static final String YER = "YER";
    static final String SAR = "SAR";

    private MoneyCurrency() {}

    static String requireSupported(String currencyCode) {
        if (!YER.equals(currencyCode) && !SAR.equals(currencyCode)) {
            throw new IllegalArgumentException("العملة غير مدعومة؛ اختر YER أو SAR");
        }
        return currencyCode;
    }

    static List<String> all() {
        return Arrays.asList(YER, SAR);
    }

    static String accountId(String baseAccountId, String currencyCode) {
        requireSupported(currencyCode);
        return YER.equals(currencyCode) ? baseAccountId : baseAccountId + "-" + currencyCode;
    }

    static String cashBoxId(String currencyCode) {
        requireSupported(currencyCode);
        return YER.equals(currencyCode) ? "system-main-cash-box"
                : "system-main-cash-box-" + currencyCode;
    }
}
