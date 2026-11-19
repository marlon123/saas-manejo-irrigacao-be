package com.irrigation.domain.model;

import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.SprinklerRate;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representando um plano de manejo: vínculo de uma área com uma cultura e um perfil de solo,
 * a partir de uma data, com o fator de disponibilidade (F) e a taxa de aplicação do aspersor.
 * (Req 4.1–4.5) — invariantes aplicados na construção.
 * <p>
 * {@code endDate} é opcional; plano sem data final está ativo. {@code endDate} não pode ser anterior a
 * {@code startDate} (A-02). Regras que dependem de outros registros (um único plano ativo por área,
 * sobreposição, último dia calculado, troca de cultura ou solo com balanço) ficam na camada de aplicação.
 * Área, cultura e solo não são alterados depois da criação.
 */
public class ManagementPlan {

    private final UUID id;
    private final UUID areaId;
    private final UUID cropId;
    private final UUID soilProfileId;
    private final LocalDate startDate;
    private LocalDate endDate;
    private AvailabilityFactor availabilityFactor;
    private SprinklerRate sprinklerRate;

    /** Cria um plano ativo (sem data final). */
    public ManagementPlan(UUID id, UUID areaId, UUID cropId, UUID soilProfileId, LocalDate startDate,
                          AvailabilityFactor availabilityFactor, SprinklerRate sprinklerRate) {
        this(id, areaId, cropId, soilProfileId, startDate, null, availabilityFactor, sprinklerRate);
    }

    public ManagementPlan(UUID id, UUID areaId, UUID cropId, UUID soilProfileId, LocalDate startDate,
                          LocalDate endDate, AvailabilityFactor availabilityFactor, SprinklerRate sprinklerRate) {
        this.id = Objects.requireNonNull(id, "id não pode ser nulo");
        this.areaId = Objects.requireNonNull(areaId, "areaId não pode ser nulo");
        this.cropId = Objects.requireNonNull(cropId, "cropId não pode ser nulo");
        this.soilProfileId = Objects.requireNonNull(soilProfileId, "soilProfileId não pode ser nulo");
        validateStartDate(startDate);
        validateEndDate(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
        validateAvailabilityFactor(availabilityFactor);
        validateSprinklerRate(sprinklerRate);
        this.availabilityFactor = availabilityFactor;
        this.sprinklerRate = sprinklerRate;
    }

    /**
     * Atualiza o fator de disponibilidade e a taxa do aspersor (Req 4.5).
     * Nada é alterado se alguma validação falhar.
     */
    public void updateParameters(AvailabilityFactor newFactor, SprinklerRate newRate) {
        validateAvailabilityFactor(newFactor);
        validateSprinklerRate(newRate);
        this.availabilityFactor = newFactor;
        this.sprinklerRate = newRate;
    }

    /**
     * Encerra o plano na data informada (A-02).
     *
     * @throws IllegalArgumentException se a data for nula ou anterior a {@code startDate}
     */
    public void close(LocalDate newEndDate) {
        if (newEndDate == null) {
            throw new IllegalArgumentException("data final é obrigatória para encerrar o plano");
        }
        validateEndDate(startDate, newEndDate);
        this.endDate = newEndDate;
    }

    /**
     * Valida a data de início (Req 4.2): obrigatória.
     *
     * @throws IllegalArgumentException se a data for nula
     */
    public static void validateStartDate(LocalDate startDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("data de início é obrigatória");
        }
    }

    /**
     * Valida a data final (A-02): opcional; quando presente, não pode ser anterior à data de início.
     * Se a data de início for nula, só a presença da data final é aceita (o erro da data de início é
     * reportado por {@link #validateStartDate(LocalDate)}).
     *
     * @throws IllegalArgumentException se a data final for anterior à data de início
     */
    public static void validateEndDate(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && startDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("data final não pode ser anterior à data de início");
        }
    }

    /**
     * Valida o fator de disponibilidade (Req 4.3): obrigatório. A faixa (maior que 0 e no máximo 1,
     * até 4 casas) é garantida pelo value object {@link AvailabilityFactor}.
     *
     * @throws IllegalArgumentException se o fator for nulo
     */
    public static void validateAvailabilityFactor(AvailabilityFactor factor) {
        if (factor == null) {
            throw new IllegalArgumentException("fator de disponibilidade (F) é obrigatório");
        }
    }

    /**
     * Valida a taxa do aspersor (Req 4.4): obrigatória. A faixa (maior que 0 e no máximo 500 mm/h,
     * até 4 casas) é garantida pelo value object {@link SprinklerRate}.
     *
     * @throws IllegalArgumentException se a taxa for nula
     */
    public static void validateSprinklerRate(SprinklerRate rate) {
        if (rate == null) {
            throw new IllegalArgumentException("taxa de aplicação do aspersor é obrigatória");
        }
    }

    /** Plano sem data final. */
    public boolean isActive() { return endDate == null; }

    public UUID getId() { return id; }
    public UUID getAreaId() { return areaId; }
    public UUID getCropId() { return cropId; }
    public UUID getSoilProfileId() { return soilProfileId; }
    public LocalDate getStartDate() { return startDate; }
    /** Data final do plano; nula enquanto o plano está ativo. */
    public LocalDate getEndDate() { return endDate; }
    public AvailabilityFactor getAvailabilityFactor() { return availabilityFactor; }
    public SprinklerRate getSprinklerRate() { return sprinklerRate; }
}
