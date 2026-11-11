# ============================================================================
# Imagem do irrigation-management-backend (Req 11.1)
#
# Estágio 1 (build): JDK 21 + Gradle Wrapper. Executa `gradlew build`, que inclui
#   `check`: testes unitários e meta de cobertura do JaCoCo. Os testes de integração
#   (@Tag("integration")) ficam fora, pois exigem Docker dentro do build.
# Estágio 2 (runtime): só JRE 21 + JAR executável, rodando como usuário não-root.
#
# Build:  docker build -t irrigation-backend .
# ============================================================================

# ---------------------------------------------------------------- build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Wrapper e scripts de build primeiro: a camada de dependências só é refeita quando eles mudam.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Cache do Gradle (distribuição e dependências) reaproveitado entre builds (BuildKit).
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --quiet dependencies

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain build

# ---------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre

# Usuário de sistema sem shell de login nem diretório home.
RUN groupadd --system app \
    && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
# O JAR pertence ao root e é apenas lido pelo usuário da aplicação.
COPY --from=build /workspace/build/libs/irrigation-backend.jar app.jar

USER app
EXPOSE 8080

# Limita o heap a uma fração da memória do contêiner; sobrescreva com -e JAVA_OPTS=...
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
