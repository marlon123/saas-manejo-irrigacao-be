// ============================================================================
// Build configuration for irrigation-management-backend
// Spring Boot 3.3.x + Java 21 + Gradle Kotlin DSL
// ============================================================================

plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    jacoco
}

group = "com.irrigation"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// ============================================================================
// Repositories
// ============================================================================

repositories {
    mavenCentral()
}

// ============================================================================
// Dependency versions (centralized)
// ============================================================================

val mapstructVersion = "1.6.3"
val springdocVersion = "2.6.0"
// 1.21.4+: o docker-java das versões anteriores usa a API 1.32, recusada pelo Docker Engine 29+
// (mínimo 1.44). A propriedade abaixo também ajusta o BOM do Spring Boot, que gerencia o
// testcontainers core transitivo.
val testcontainersVersion = "1.21.4"
extra["testcontainers.version"] = testcontainersVersion
val jqwikVersion = "1.9.0"
val archunitVersion = "1.3.0"
val logstashEncoderVersion = "8.0"

// ============================================================================
// Dependencies — organized by responsibility
// ============================================================================

dependencies {
    // --- Spring Boot starters ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- Database ---
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // --- Mapping (MapStruct) ---
    implementation("org.mapstruct:mapstruct:${mapstructVersion}")
    annotationProcessor("org.mapstruct:mapstruct-processor:${mapstructVersion}")

    // --- Logging estruturado em JSON (Req 9.4) ---
    implementation("net.logstash.logback:logstash-logback-encoder:${logstashEncoderVersion}")

    // --- API Documentation ---
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${springdocVersion}")

    // --- Test dependencies ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter:${testcontainersVersion}")
    testImplementation("org.testcontainers:postgresql:${testcontainersVersion}")

    // --- Property-based testing (jqwik) ---
    testImplementation("net.jqwik:jqwik:${jqwikVersion}")

    // --- Architectural tests (ArchUnit) ---
    testImplementation("com.tngtech.archunit:archunit-junit5:${archunitVersion}")
}

// ============================================================================
// Test configuration — JUnit 5 + jqwik
// ============================================================================

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        showStandardStreams = false
    }
}

// Testes unitários: tudo, exceto os marcados com @Tag("integration") (exigem Docker).
tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

// Testes de integração com PostgreSQL via Testcontainers. Pulados se o Docker não estiver disponível.
val integrationTest by tasks.registering(Test::class) {
    description = "Executa os testes de integração (@Tag(\"integration\")) com PostgreSQL via Testcontainers."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
}

// ============================================================================
// JaCoCo — minimum 80% line coverage (Req 10.6)
// ============================================================================

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// ============================================================================
// Quality gate — check runs tests + coverage verification before build
// ============================================================================

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// ============================================================================
// Artefato — um único JAR executável com nome fixo, copiado pelo Dockerfile
// ============================================================================

tasks.bootJar {
    archiveFileName.set("irrigation-backend.jar")
}

// O JAR "plain" (sem dependências) não é usado; desligado para não haver dois JARs em build/libs.
tasks.jar {
    enabled = false
}

// ============================================================================
// Build — always runs check before packaging
// ============================================================================

tasks.build {
    dependsOn(tasks.check)
}
