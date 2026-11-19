package com.irrigation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GravimetricWaterContentTest {

    @Test
    void acceptsBoundariesAndTwoDecimals() {
        assertThat(new GravimetricWaterContent(new BigDecimal("0")).value()).isEqualByComparingTo("0");
        assertThat(new GravimetricWaterContent(new BigDecimal("100.00")).value()).isEqualByComparingTo("100");
        assertThat(new GravimetricWaterContent(new BigDecimal("25.37")).value()).isEqualByComparingTo("25.37");
        assertThat(new GravimetricWaterContent(new BigDecimal("25.500")).value()).isEqualByComparingTo("25.5");
    }

    @Test
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> new GravimetricWaterContent(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GravimetricWaterContent(new BigDecimal("100.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMoreThanTwoDecimals() {
        assertThatThrownBy(() -> new GravimetricWaterContent(new BigDecimal("25.123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 casas");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new GravimetricWaterContent(null)).isInstanceOf(NullPointerException.class);
    }
}
