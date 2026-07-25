package com.mrboard.common.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MathUtils {

    private MathUtils() {}

    /**
     * 计算整数列表的中位数
     */
    public static double median(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
    }

    /**
     * 计算 double 列表的中位数
     */
    public static double medianDouble(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
    }

    /**
     * 安全除法，避免除以零
     */
    public static BigDecimal safeDivide(Number numerator, Number denominator, int scale) {
        if (numerator == null || denominator == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal num = BigDecimal.valueOf(numerator.doubleValue());
        BigDecimal den = BigDecimal.valueOf(denominator.doubleValue());
        if (den.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return num.divide(den, scale, RoundingMode.HALF_UP);
    }
}
