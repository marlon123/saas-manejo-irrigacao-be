package com.irrigation.application.service;

import com.irrigation.application.dto.AreaInput;
import com.irrigation.application.dto.AreaOutput;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.enums.IrrigationSystemType;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AreaServiceTest {

    @Mock
    AreaRepository repository;

    @InjectMocks
    AreaService service;

    private static AreaInput input(String name, String hectares, String flow, String lat, String lon) {
        return new AreaInput(name, hectares == null ? null : bd(hectares), lat == null ? null : bd(lat),
                lon == null ? null : bd(lon), null, IrrigationSystemType.DRIP,
                flow == null ? null : bd(flow), null);
    }

    private static List<String> fields(ValidationFailedException e) {
        return e.getViolations().stream().map(FieldViolation::field).toList();
    }

    // ------------------------------------------------------------------ create

    @Test
    void createPersistsAndReturnsTheArea() {
        when(repository.save(any(Area.class))).thenAnswer(i -> i.getArgument(0));

        AreaOutput output = service.create(TestData.areaInput());

        assertThat(output.id()).isNotNull();
        assertThat(output.name()).isEqualTo("Talhão 1");
        assertThat(output.latitude()).isEqualByComparingTo("-23.5");
        verify(repository).save(any(Area.class));
    }

    @Test
    void createReportsEveryInvalidFieldAtOnce() {
        AreaInput invalid = input("   ", "0", "-1", "100", null);

        assertThatThrownBy(() -> service.create(invalid))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder(
                                "name", "areaHectares", "systemFlowRate", "geographicLocation"));
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() -> service.create(input("A", "1", "1", "90.5", "10")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("geographicLocation"));
    }

    @Test
    void createRejectsMissingRequiredFields() {
        AreaInput empty = new AreaInput(null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.create(empty))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder(
                                "name", "areaHectares", "systemFlowRate", "irrigationSystemType"));
    }

    // ------------------------------------------------------------------ update

    @Test
    void updateReplacesTheExistingArea() {
        Area existing = TestData.area();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(Area.class))).thenAnswer(i -> i.getArgument(0));

        AreaOutput output = service.update(existing.getId(), input("Novo nome", "20", "50", null, null));

        assertThat(output.id()).isEqualTo(existing.getId());
        assertThat(output.name()).isEqualTo("Novo nome");
        assertThat(output.areaHectares()).isEqualByComparingTo("20");
    }

    @Test
    void updateFailsWithNotFoundForAnUnknownArea() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, TestData.areaInput()))
                .isInstanceOf(EntityNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsInvalidDataWithoutSaving() {
        Area existing = TestData.area();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(), input("", "5", "5", null, null)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("name"));
        verify(repository, never()).save(any());
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deleteRemovesAnUnreferencedArea() {
        Area existing = TestData.area();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.isReferencedByRecommendations(existing.getId())).thenReturn(false);

        service.delete(existing.getId());

        verify(repository).deleteById(existing.getId());
    }

    @Test
    void deleteFailsWithNotFoundForAnUnknownArea() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(EntityNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void deleteIsRejectedWhenTheAreaHasRecommendations() {
        Area existing = TestData.area();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.isReferencedByRecommendations(existing.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(existing.getId())).isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).deleteById(any());
    }

    // ------------------------------------------------------------------ get / list

    @Test
    void getByIdReturnsTheArea() {
        Area existing = TestData.area();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThat(service.getById(existing.getId()).id()).isEqualTo(existing.getId());
    }

    @Test
    void getByIdFailsWithNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listUsesDefaultPagination() {
        when(repository.findAll(any(PageRequest.class), any(), any())).thenReturn(List.of(TestData.area()));
        when(repository.count()).thenReturn(1L);

        PagedResult<AreaOutput> result = service.list(null, null, null, null);

        verify(repository).findAll(PageRequest.of(0, 20), null, null);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
    }

    @Test
    void listPassesTheRequestedSort() {
        when(repository.findAll(any(PageRequest.class), any(), any())).thenReturn(List.of());
        when(repository.count()).thenReturn(0L);

        PagedResult<AreaOutput> result = service.list(2, 5, "areaHectares", "desc");

        verify(repository).findAll(PageRequest.of(2, 5), "areaHectares", "desc");
        assertThat(result.content()).isEmpty();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void listRejectsInvalidPaginationWithoutQueryingTheRepository() {
        assertThatThrownBy(() -> service.list(-1, 101, null, null))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("page", "size"));
        verifyNoInteractions(repository);
    }

    @Test
    void listRejectsAnInvalidSortFieldOrDirection() {
        assertThatThrownBy(() -> service.list(null, null, "latitude", "sideways"))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("sort", "direction"));
    }

    @Test
    void outputKeepsBigDecimalScale() {
        when(repository.save(any(Area.class))).thenAnswer(i -> i.getArgument(0));
        AreaOutput output = service.create(input("A", "10.50", "1", null, null));
        assertThat(output.areaHectares()).isEqualTo(new BigDecimal("10.50"));
    }
}
