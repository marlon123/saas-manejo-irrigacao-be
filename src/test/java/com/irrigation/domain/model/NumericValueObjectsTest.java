package com.irrigation.domain.model;

import com.irrigation.domain.model.valueobjects.GeographicLocation;
import com.irrigation.support.RangeSpec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Faixas válidas dos value objects numéricos, nos limites exatos definidos pelos requisitos.
 */
class NumericValueObjectsTest {

    @TestFactory
    Stream<DynamicTest> acceptValuesAtAndInsideTheBounds() {
        List<DynamicTest> tests = new ArrayList<>();
        for (RangeSpec spec : RangeSpec.ALL) {
            tests.add(DynamicTest.dynamicTest(spec + " aceita o menor valor válido",
                    () -> assertThatCode(() -> spec.factory().apply(spec.lowestValid())).doesNotThrowAnyException()));
            if (spec.max() != null) {
                tests.add(DynamicTest.dynamicTest(spec + " aceita o limite superior",
                        () -> assertThatCode(() -> spec.factory().apply(spec.max())).doesNotThrowAnyException()));
            }
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> rejectValuesOutsideTheBounds() {
        List<DynamicTest> tests = new ArrayList<>();
        for (RangeSpec spec : RangeSpec.ALL) {
            tests.add(DynamicTest.dynamicTest(spec + " rejeita valor abaixo do limite inferior",
                    () -> assertThatThrownBy(() -> spec.factory().apply(spec.highestBelow()))
                            .isInstanceOf(IllegalArgumentException.class)));
            if (spec.max() != null) {
                tests.add(DynamicTest.dynamicTest(spec + " rejeita valor acima do limite superior",
                        () -> assertThatThrownBy(() -> spec.factory().apply(spec.lowestAbove()))
                                .isInstanceOf(IllegalArgumentException.class)));
            }
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> rejectNull() {
        return RangeSpec.ALL.stream().map(spec -> DynamicTest.dynamicTest(spec + " rejeita nulo",
                () -> assertThatThrownBy(() -> spec.factory().apply(null)).isInstanceOf(NullPointerException.class)));
    }

    @Test
    void geographicLocationAcceptsTheExtremes() {
        assertThatCode(() -> new GeographicLocation(new BigDecimal("-90"), new BigDecimal("-180")))
                .doesNotThrowAnyException();
        assertThatCode(() -> new GeographicLocation(new BigDecimal("90"), new BigDecimal("180")))
                .doesNotThrowAnyException();
    }

    @Test
    void geographicLocationRejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() -> new GeographicLocation(new BigDecimal("90.01"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("latitude");
        assertThatThrownBy(() -> new GeographicLocation(new BigDecimal("-90.01"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("latitude");
        assertThatThrownBy(() -> new GeographicLocation(BigDecimal.ZERO, new BigDecimal("180.01")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("longitude");
        assertThatThrownBy(() -> new GeographicLocation(BigDecimal.ZERO, new BigDecimal("-180.01")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("longitude");
    }
}
