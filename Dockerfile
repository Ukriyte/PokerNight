FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend-app
COPY frontend-app/package.json frontend-app/package-lock.json ./
RUN npm ci
COPY frontend-app/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS java-build
WORKDIR /workspace
COPY pom.xml ./
COPY game-engine/pom.xml game-engine/pom.xml
COPY application/pom.xml application/pom.xml
COPY web/pom.xml web/pom.xml
COPY game-engine/src game-engine/src
COPY application/src application/src
COPY web/src web/src
COPY --from=frontend-build /workspace/frontend-app/dist frontend-app/dist
RUN mvn -Pbundle-frontend -pl web -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system pokernight && useradd --system --gid pokernight --home-dir /app pokernight
COPY --from=java-build /workspace/web/target/web-0.1.0-SNAPSHOT.jar app.jar
RUN mkdir -p /app/data && chown -R pokernight:pokernight /app
USER pokernight
VOLUME ["/app/data"]
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar", "--spring.profiles.active=file", "--pokernight.persistence.directory=/app/data"]
