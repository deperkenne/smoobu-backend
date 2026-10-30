# Étape 1 : compilation du JAR avec le wrapper Maven du projet
FROM eclipse-temurin:24-jdk AS build
WORKDIR /build

# Dépendances d'abord : la couche reste en cache tant que pom.xml ne change pas
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -B package -DskipTests

# Étape 2 : image d'exécution, sans JDK ni sources
FROM eclipse-temurin:24-jre
WORKDIR /app

COPY --from=build /build/target/dd-*.jar app.jar

# L'application ne tourne jamais en root
USER 1001:1001

EXPOSE 8080

# La JVM adapte sa mémoire à la limite du conteneur
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
