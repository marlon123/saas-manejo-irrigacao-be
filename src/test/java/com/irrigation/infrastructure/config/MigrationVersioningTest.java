package com.irrigation.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Convenção de versionamento das migrações Flyway (Req 8.4): versões inteiras,
 * únicas, sequenciais a partir de 1 e com descrição.
 */
class MigrationVersioningTest {

    private static final Pattern NAME = Pattern.compile("V(\\d+)__(.+)\\.sql");

    private List<Integer> versions() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql");
        List<Integer> versions = new ArrayList<>();
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            Matcher matcher = NAME.matcher(filename);
            assertThat(matcher.matches())
                    .as("o arquivo %s deve seguir o padrão V<n>__<descricao>.sql", filename).isTrue();
            versions.add(Integer.parseInt(matcher.group(1)));
        }
        return versions.stream().sorted().toList();
    }

    @Test
    void migrationsExistAndVersionsAreUniqueAndSequentialFromOne() throws IOException {
        List<Integer> versions = versions();

        assertThat(versions).isNotEmpty();
        assertThat(versions).doesNotHaveDuplicates();
        for (int i = 0; i < versions.size(); i++) {
            assertThat(versions.get(i)).as("posição %d", i).isEqualTo(i + 1);
        }
    }

    @Test
    void initialMigrationCreatesTheRequiredTables() throws IOException {
        Resource initial = new PathMatchingResourcePatternResolver()
                .getResource("classpath:db/migration/V1__init_schema.sql");
        String sql = new String(initial.getInputStream().readAllBytes());

        for (String table : List.of("areas", "crops", "crop_stages", "soil_profiles", "recommendations")) {
            assertThat(sql).containsIgnoringCase("CREATE TABLE " + table);
        }
    }
}
