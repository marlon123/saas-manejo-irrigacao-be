package com.irrigation.domain.model;

import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root da meteorologia diária de uma área: ETP e precipitação (P) de uma data.
 * (Req 5.1, 5.2 do módulo de balanço hídrico) — invariantes aplicados na construção.
 *
 * <p>Imutável: a regravação de uma data (upsert) é resolvida pela porta de persistência,
 * que substitui os valores do registro existente. A unicidade por área e data é garantida pelo banco.
 * A regra de "data não posterior à data atual em UTC" depende do relógio e fica no serviço de aplicação.
 */
public final class DailyWeather {

    private final UUID id;
    private final UUID areaId;
    private final LocalDate date;
    private final EtpDepth etp;
    private final PrecipitationDepth precipitation;

    public DailyWeather(UUID id, UUID areaId, LocalDate date, EtpDepth etp, PrecipitationDepth precipitation) {
        this.id = Objects.requireNonNull(id, "id não pode ser nulo");
        this.areaId = Objects.requireNonNull(areaId, "área não pode ser nula");
        this.date = Objects.requireNonNull(date, "data não pode ser nula");
        this.etp = Objects.requireNonNull(etp, "ETP não pode ser nula");
        this.precipitation = Objects.requireNonNull(precipitation, "precipitação não pode ser nula");
    }

    public UUID getId() { return id; }
    public UUID getAreaId() { return areaId; }
    public LocalDate getDate() { return date; }
    public EtpDepth getEtp() { return etp; }
    public PrecipitationDepth getPrecipitation() { return precipitation; }
}
