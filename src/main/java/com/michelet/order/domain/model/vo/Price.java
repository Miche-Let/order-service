package com.michelet.order.domain.model.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Price(BigDecimal value) {
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");

    public Price {
        if (value == null) {
            throw new IllegalArgumentException("가격 정보가 누락되었습니다.");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("가격은 0원 이상이어야 합니다.");
        }

        // 정규화: 소수점 2자리 반올림
        value = value.setScale(2, RoundingMode.HALF_UP);

        // Precision 검증 (12자리 초과 방지)
        if (value.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException("허용 최대 금액(9,999,999,999.99)을 초과했습니다.");
        }
    }

    public static Price of(BigDecimal value) {
        return new Price(value);
    }
}
