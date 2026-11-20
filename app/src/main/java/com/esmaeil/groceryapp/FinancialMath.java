package com.esmaeil.groceryapp;

import java.math.BigInteger;

final class FinancialMath {
    private static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    private FinancialMath() {}

    static long add(long left, long right) {
        return checked(BigInteger.valueOf(left).add(BigInteger.valueOf(right)));
    }

    static long multiply(long left, long right) {
        return checked(BigInteger.valueOf(left).multiply(BigInteger.valueOf(right)));
    }

    static long exactLong(BigInteger value) {
        return checked(value);
    }

    private static long checked(BigInteger result) {
        if (result.compareTo(MIN_LONG) < 0 || result.compareTo(MAX_LONG) > 0) {
            throw new ArithmeticException("قيمة مالية تتجاوز النطاق المدعوم");
        }
        return result.longValue();
    }
}
