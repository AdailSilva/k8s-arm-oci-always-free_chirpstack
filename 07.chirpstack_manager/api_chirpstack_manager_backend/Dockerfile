# =====================================================================
#  ChirpStack Manager API - imagem de produção (perfil Maven/Spring "production")
#
#    docker buildx build --platform linux/arm64 -t <registro>/api-chirpstack-manager-backend:<versão> .
#
#  Portas: development 8081 · staging 8082 · production 8083 · actuator 8084 (todos).
#  Para rodar outro perfil localmente use o Maven (mvn spring-boot:run -Pstaging), como nos
#  demais backends do repositório.
# =====================================================================

# ── Stage 1: Build ──────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Resiliência contra falhas transitórias do Maven Central: um "502 Bad Gateway" ao baixar um
# artefato é instabilidade do repo.maven.apache.org, não do projeto. Por padrão o Maven Resolver
# só repete 429/503 e desiste no 502.
#   - MAVEN_ARGS (lido pelo Maven 3.9+ em todo comando `mvn`): repete até 5x também em
#     500/502/504, com backoff do resolver, e timeouts explícitos para não travar.
#   - Loop no go-offline: se mesmo assim falhar, tenta de novo (até 4x) com espera crescente.
#     O cache mount /root/.m2 persiste entre as tentativas: cada rodada só baixa o que faltou.
ENV MAVEN_ARGS="-B \
 -Daether.connector.http.retryHandler.count=5 \
 -Daether.connector.http.retryHandler.serviceUnavailable=429,500,502,503,504 \
 -Daether.connector.connectTimeout=30000 \
 -Daether.connector.requestTimeout=120000"

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    for i in 1 2 3 4; do \
      mvn dependency:go-offline -Pproduction -q && exit 0; \
      echo ">>> go-offline falhou (tentativa $i/4), nova tentativa em $((i*15))s..."; \
      sleep $((i*15)); \
    done; \
    echo ">>> go-offline falhou após 4 tentativas"; exit 1

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -Pproduction -q

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Metadados da imagem
LABEL maintainer="AdailSilva <adail101@hotmail.com>" \
      app="api-chirpstack-manager-backend" \
      description="ChirpStack-Manager – Backend Spring Boot"

# postgresql17-client: pg_dump, pg_restore e psql do módulo de backup (mesma versão do servidor
# PostgreSQL 17 do painel; o pg_dump precisa ser igual ou mais novo que o servidor).
# tzdata: fuso horário da grade do backup automático (APP_TIMEZONE).
RUN apk add --no-cache postgresql17-client tzdata \
    && addgroup -S app && adduser -S -G app -u 10001 app \
    && mkdir -p /var/backups/chirpstack-manager \
    && chown 10001:app /var/backups/chirpstack-manager
WORKDIR /app
COPY --from=builder /app/target/api-chirpstack-manager-backend.jar app.jar
USER 10001
# 8081/8082/8083 = aplicação (development/staging/production) | 8084 = actuator/probes
EXPOSE 8081 8082 8083 8084
ENV SPRING_PROFILES_ACTIVE=production \
    APP_BACKUP_DIR=/var/backups/chirpstack-manager \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD wget -qO- http://127.0.0.1:8084/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
