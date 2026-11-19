package com.irrigation.domain.model;

import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root da Irrigação Real (IR) aplicada em uma área numa data.
 * (Req 6.1, 6.3 do módulo de balanço hídrico) — invariantes aplicados na construção.
 *
 * <p>Imutável: a regravação de uma data (upsert) é resolvida pela porta de persistência.
 * Uma lâmina igual a zero é um valor válido e conta como presente. A unicidade por área e data é
 * garantida pelo banco, e a regra de data futura (UTC) fica no serviço de aplicação.
 */
public final class AppliedIrrigation {

    private final UUID id;
    private final UUID areaId;
    private final LocalDate date;
    private final AppliedIrrigationDepth depth;

    public AppliedIrrigation(UUID id, UUID areaId, LocalDate date, AppliedIrrigationDepth depth) {
        this.id = Objects.requireNonNull(id, "id não pode ser nulo");
        this.areaId = Objects.requireNonNull(areaId, "área não pode ser nula");
        this.date = Objects.requireNonNull(date, "data não pode ser nula");
        this.depth = Objects.requireNonNull(depth, "irrigação real não pode ser nula");
    }

    public UUID getId() { return id; }
    public UUID getAreaId() { return areaId; }
    public LocalDate getDate() { return date; }
    public AppliedIrrigationDepth getDepth() { return depth; }
}
