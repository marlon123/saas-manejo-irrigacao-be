package com.irrigation.domain.waterbalance;

import com.irrigation.application.waterbalance.PresentationRounding;
import com.irrigation.application.waterbalance.PresentationRounding.OperatingTimeView;
import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cenários A, B, C e E do requirements.md (seção 6), linha a linha.
 * Todos os valores são comparados com {@code compareTo}, sem arredondamento intermediário.
 *
 * (Req 7.1, 8.1, 8.2, 8.3, 8.5, 9.2, 9.3, 9.4, 9.6, 10.1, 10.3, 15.2, 15.6)
 */
class WaterBalanceScenariosTest {

    private final WaterBalanceCalculator calculator = new WaterBalanceCalculator();

    /** Entradas diárias: ETP, P e IR. */
    private record Day(String etp, String p, String ir) {
    }

    /** Linha esperada da tabela: ETc, AFDi, AFDf, I, recomendado, horas, h e min. */
    private record Expected(String etc, String afdi, String afdf, String ideal,
                            boolean recommended, String hours, int wholeHours, int minutes) {
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private WaterBalanceInputs inputs(Day d, String f, String rate, BigDecimal previousAfdFinal) {
        boolean first = previousAfdFinal == null;
        return new WaterBalanceInputs(
                new EtpDepth(bd(d.etp())),
                new PrecipitationDepth(bd(d.p())),
                new AppliedIrrigationDepth(bd(d.ir())),
                new GravimetricWaterContent(bd("30")),
                new GravimetricWaterContent(bd("15")),
                new BulkDensity(bd("1.2")),
                new RootDepth(bd("400")),
                new Kc(bd("0.8")),
                new AvailabilityFactor(bd(f)),
                new SprinklerRate(bd(rate)),
                first,
                previousAfdFinal);
    }

    /** Encadeia os dias passando a AFDf do dia anterior e confere cada linha. */
    private void runChain(List<Day> days, List<Expected> rows, String f, String cad) {
        assertThat(days).hasSameSizeAs(rows);
        BigDecimal previous = null;
        for (int i = 0; i < days.size(); i++) {
            String label = "dia " + (i + 1);
            WaterBalanceResult r = calculator.calculate(inputs(days.get(i), f, "6", previous));
            Expected e = rows.get(i);

            assertThat(r.cad()).as(label + " CAD").isEqualByComparingTo(cad);
            assertThat(r.etc()).as(label + " ETc").isEqualByComparingTo(e.etc());
            assertThat(r.afdInitial()).as(label + " AFDi").isEqualByComparingTo(e.afdi());
            assertThat(r.afdFinal()).as(label + " AFDf").isEqualByComparingTo(e.afdf());
            assertThat(r.idealIrrigation()).as(label + " I").isEqualByComparingTo(e.ideal());
            assertThat(r.recommended()).as(label + " recomendado").isEqualTo(e.recommended());
            assertThat(r.operatingTimeHours()).as(label + " horas")
                    .isEqualByComparingTo(e.hours());
            assertThat(r.noRecommendationReason()).as(label + " motivo")
                    .isEqualTo(i == 0 ? NoRecommendationReason.FIRST_DAY_OF_FIRST_CYCLE : null);

            OperatingTimeView view = PresentationRounding.operatingTime(r.operatingTimeHours());
            assertThat(view.wholeHours()).as(label + " h").isEqualTo(e.wholeHours());
            assertThat(view.minutes()).as(label + " min").isEqualTo(e.minutes());

            previous = r.afdFinal();
        }
    }

    private static final List<Day> SCENARIO_A_DAYS = List.of(
            new Day("5.00", "0", "0"),
            new Day("6.25", "0", "0"),
            new Day("7.50", "2", "5"),
            new Day("6.25", "0", "0"),
            new Day("10.00", "0", "12"),
            new Day("5.00", "0", "0"),
            new Day("15.00", "0", "0"));

    @Test
    @DisplayName("Cenário A: 7 dias, CAD 72, AFDi do dia 1 = 36, continuidade e restrição do primeiro dia")
    void scenarioA() {
        List<Expected> rows = List.of(
                new Expected("4.00", "36.00", "32.00", "4.00", false, "0", 0, 0),
                new Expected("5.00", "32.00", "27.00", "5.00", true, "0.8333333333", 0, 50),
                new Expected("6.00", "27.00", "28.00", "-1.00", false, "0", 0, 0),
                new Expected("5.00", "28.00", "23.00", "5.00", true, "0.8333333333", 0, 50),
                new Expected("8.00", "23.00", "27.00", "-4.00", false, "0", 0, 0),
                new Expected("4.00", "27.00", "23.00", "4.00", true, "0.6666666667", 0, 40),
                new Expected("12.00", "23.00", "11.00", "12.00", true, "2", 2, 0));

        runChain(SCENARIO_A_DAYS, rows, "0.50", "72");
    }

    @Test
    @DisplayName("Cenário A: horas apresentadas com 4 casas (0,8333; 0,6667; 2,0000)")
    void scenarioAPresentedHours() {
        WaterBalanceInputs day2 = inputs(SCENARIO_A_DAYS.get(1), "0.50", "6", bd("32"));
        WaterBalanceInputs day6 = inputs(SCENARIO_A_DAYS.get(5), "0.50", "6", bd("27"));
        WaterBalanceInputs day7 = inputs(SCENARIO_A_DAYS.get(6), "0.50", "6", bd("23"));

        assertThat(PresentationRounding.operatingTime(calculator.calculate(day2).operatingTimeHours()).hours())
                .isEqualByComparingTo("0.8333");
        assertThat(PresentationRounding.operatingTime(calculator.calculate(day6).operatingTimeHours()).hours())
                .isEqualByComparingTo("0.6667");
        assertThat(PresentationRounding.operatingTime(calculator.calculate(day7).operatingTimeHours()).hours())
                .isEqualByComparingTo("2.0000");
    }

    @Test
    @DisplayName("Cenário B: F = 0,90, AFDi = 64,80 e AFDf = 80,80 acima do CAD mantida e usada no dia 2")
    void scenarioB() {
        List<Day> days = List.of(
                new Day("5.00", "20", "0"),
                new Day("5.00", "0", "0"));
        List<Expected> rows = List.of(
                new Expected("4.00", "64.80", "80.80", "-16.00", false, "0", 0, 0),
                new Expected("4.00", "80.80", "76.80", "4.00", true, "0.6666666667", 0, 40));

        runChain(days, rows, "0.90", "72");

        WaterBalanceResult day1 = calculator.calculate(inputs(days.get(0), "0.90", "6", null));
        assertThat(day1.afdFinal()).isGreaterThan(day1.cad());
    }

    @ParameterizedTest(name = "Cenário C: I = {0}, taxa = {1} -> {2} h, {3} min totais")
    @CsvSource({
            "12,  6, 2.0000, 120, 2, 0",
            "1,   6, 0.1667, 10,  0, 10",
            "7,   6, 1.1667, 70,  1, 10",
            "2.5, 4, 0.6250, 38,  0, 38"
    })
    void scenarioC(String ideal, String rate, String hours, int totalMinutes, int wholeHours, int minutes) {
        // Dia 2 de um plano: ETP = 20 gera ETc = 16; P = 16 - I produz exatamente o I desejado
        // (P e ETP ficam com no máximo 2 casas decimais).
        BigDecimal precipitation = bd("16").subtract(bd(ideal));
        Day day = new Day("20", precipitation.toPlainString(), "0");
        WaterBalanceInputs in = inputs(day, "0.50", rate, bd("30"));

        WaterBalanceResult r = calculator.calculate(in);

        assertThat(r.idealIrrigation()).isEqualByComparingTo(ideal);
        assertThat(r.recommended()).isTrue();
        OperatingTimeView view = PresentationRounding.operatingTime(r.operatingTimeHours());
        assertThat(view.hours()).isEqualByComparingTo(hours);
        assertThat(view.wholeHours() * 60 + view.minutes()).isEqualTo(totalMinutes);
        assertThat(view.wholeHours()).isEqualTo(wholeHours);
        assertThat(view.minutes()).isEqualTo(minutes);
    }

    @Test
    @DisplayName("Cenário C: 2,5 mm a 4 mm/h dá 37,5 min, que viram 38 por HALF_UP")
    void scenarioCHalfUpMinutes() {
        BigDecimal hours = new BigDecimal("2.5").divide(bd("4"));
        assertThat(hours).isEqualByComparingTo("0.625");
        OperatingTimeView view = PresentationRounding.operatingTime(hours);
        assertThat(view.wholeHours()).isZero();
        assertThat(view.minutes()).isEqualTo(38);
    }

    @ParameterizedTest(name = "Cenário C: I não positivo ({0}) gera 0 h 00 min")
    @CsvSource({"0", "-1.00", "-4.00"})
    void scenarioCNonPositiveIdeal(String ideal) {
        // AFDf = AFDi - I; P entra no lugar de ETc para obter I = 0 ou negativo no dia 2.
        BigDecimal needed = bd(ideal).negate(); // P + IR - ETc com ETP zero
        WaterBalanceInputs in = new WaterBalanceInputs(
                new EtpDepth(bd("0")),
                new PrecipitationDepth(needed),
                new AppliedIrrigationDepth(bd("0")),
                new GravimetricWaterContent(bd("30")),
                new GravimetricWaterContent(bd("15")),
                new BulkDensity(bd("1.2")),
                new RootDepth(bd("400")),
                new Kc(bd("0.8")),
                new AvailabilityFactor(bd("0.50")),
                new SprinklerRate(bd("6")),
                false,
                bd("30"));

        WaterBalanceResult r = calculator.calculate(in);

        assertThat(r.idealIrrigation()).isEqualByComparingTo(ideal);
        assertThat(r.recommended()).isFalse();
        assertThat(r.operatingTimeHours()).isEqualByComparingTo("0");
        OperatingTimeView view = PresentationRounding.operatingTime(r.operatingTimeHours());
        assertThat(view.hours()).isEqualByComparingTo("0");
        assertThat(view.wholeHours()).isZero();
        assertThat(view.minutes()).isZero();
    }

    @Test
    @DisplayName("Cenário E: ETP do dia 2 corrigida de 6,25 para 7,50 propaga para os dias 3 e 4")
    void scenarioE() {
        List<Day> days = List.of(
                new Day("5.00", "0", "0"),
                new Day("7.50", "0", "0"),
                new Day("7.50", "2", "5"),
                new Day("6.25", "0", "0"));
        List<Expected> rows = List.of(
                new Expected("4.00", "36.00", "32.00", "4.00", false, "0", 0, 0),
                new Expected("6.00", "32.00", "26.00", "6.00", true, "1.0000000000", 1, 0),
                new Expected("6.00", "26.00", "27.00", "-1.00", false, "0", 0, 0),
                new Expected("5.00", "27.00", "22.00", "5.00", true, "0.8333333333", 0, 50));

        runChain(days, rows, "0.50", "72");

        WaterBalanceResult day2 = calculator.calculate(inputs(days.get(1), "0.50", "6", bd("32")));
        assertThat(PresentationRounding.operatingTime(day2.operatingTimeHours()).hours())
                .isEqualByComparingTo("1.0000");
    }
}
