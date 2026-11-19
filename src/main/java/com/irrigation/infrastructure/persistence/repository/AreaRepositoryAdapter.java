package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.domain.model.Area;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.infrastructure.persistence.mapper.AreaPersistenceMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa a porta de domínio {@link AreaRepository} com Spring Data JPA.
 */
@Repository
@Transactional
public class AreaRepositoryAdapter implements AreaRepository {

    /** Campos de ordenação aceitos (Req 1.5) mapeados para propriedades da entidade. */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "name", "name",
            "areaHectares", "areaHectares");

    private final AreaJpaRepository jpa;
    private final AreaPersistenceMapper mapper;

    public AreaRepositoryAdapter(AreaJpaRepository jpa, AreaPersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Area save(Area area) {
        return mapper.toDomain(jpa.save(mapper.toEntity(area)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Area> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        try {
            jpa.deleteById(id);
            jpa.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw translateOrRethrow(e, id);
        }
    }

    private static RuntimeException translateOrRethrow(RuntimeException e, UUID id) {
        RuntimeException translated = ForeignKeyViolationTranslator.translate(e, "a área", id);
        return translated != null ? translated : e;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        return jpa.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReferencedByRecommendations(UUID areaId) {
        return jpa.isReferencedByRecommendations(areaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Area> findAll(PageRequest pageRequest, String sortBy, String sortDirection) {
        String property = sortBy == null ? "name" : SORTABLE_FIELDS.get(sortBy);
        if (property == null) {
            throw new IllegalArgumentException(
                    "campo de ordenação inválido: " + sortBy + ". Valores aceitos: " + SORTABLE_FIELDS.keySet());
        }
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        if ("areaHectares".equals(property)) {
            // areaHectares pode ser nulo (Req 3.1): os nulos ficam sempre no fim, nas duas direções.
            // O Spring Data ignora nullsLast no findAll(Pageable), então a ordenação está nas consultas do repositório.
            org.springframework.data.domain.Pageable unsorted =
                    org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size());
            var page = direction == Sort.Direction.DESC
                    ? jpa.findAllOrderByAreaHectaresDescNullsLast(unsorted)
                    : jpa.findAllOrderByAreaHectaresAscNullsLast(unsorted);
            return page.getContent().stream().map(mapper::toDomain).toList();
        }
        // "id" como desempate garante paginação estável
        Sort sort = Sort.by(new Sort.Order(direction, property))
                .and(Sort.by(Sort.Direction.ASC, "id"));
        return jpa.findAll(org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size(), sort))
                .getContent().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpa.count();
    }
}
