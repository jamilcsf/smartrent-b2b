# SmartRent B2B: imagem de producao (usada pelo Render, ver render.yaml).
# Etapa 1 compila com Maven + JDK 17; etapa 2 roda so o .jar num JRE 17 enxuto.

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
# Dependencias primeiro: so rebaixam quando o pom.xml muda.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Os testes rodam no CI/local; aqui so se empacota.
RUN mvn -B -q -DskipTests package \
    && cp target/smartrent-b2b-*.jar /build/app.jar

FROM eclipse-temurin:24.0.2_12-jre
# FFmpeg: sem ele o processamento de video cai no modo basico, que NAO remove metadados
# (inclusive localizacao). Com o binario no PATH a aplicacao o usa sozinha.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --home-dir /app smartrent
WORKDIR /app
COPY --from=build /build/app.jar app.jar
# Midias em disco local (MidiaStorageDisco). Em plano gratuito o disco e efemero: some a cada deploy.
ENV MIDIA_DIRETORIO=/app/dados/midias
RUN mkdir -p /app/dados/midias && chown -R smartrent:smartrent /app
USER smartrent

# O Render informa a porta em PORT; localmente cai em 8080.
# MaxRAMPercentage: deixa folga para o processamento de imagens em instancias pequenas.
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=70 -Dserver.port=${PORT:-8080} -jar app.jar"]
