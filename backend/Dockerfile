FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY backend/pom.xml .
COPY backend/qknow-framework ./qknow-framework
COPY backend/qknow-module-ai ./qknow-module-ai
COPY backend/qknow-module-kb ./qknow-module-kb
COPY backend/qknow-module-kmc ./qknow-module-kmc
COPY backend/qknow-module-system ./qknow-module-system
COPY backend/qknow-module-app ./qknow-module-app
COPY backend/qknow-module-dm ./qknow-module-dm
COPY backend/qknow-module-ext ./qknow-module-ext
COPY backend/qknow-module-kg ./qknow-module-kg
COPY backend/qknow-module-kmc ./qknow-module-kmc
COPY backend/qknow-server ./qknow-server
RUN mvn clean package -DskipTests -pl qknow-server -am

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/qknow-server/target/*.jar app.jar
EXPOSE 8099
ENTRYPOINT ["java", "-jar", "app.jar"]
