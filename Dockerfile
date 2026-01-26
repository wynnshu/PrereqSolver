# Use Maven with Java 21 for the build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy only the pom.xml first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the source code (assuming the new src/main/java structure)
COPY src ./src
RUN mvn package

# Use Eclipse Temurin Java 21 JRE for the runtime stage
# 'alpine' or 'slim' versions keep the image size small
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/target/prereqsolver-1.0-SNAPSHOT.jar /app/app.jar

# Copy data files
COPY cornell_catalog_FA25_under6000.json /app/cornell_catalog_FA25_under6000.json
COPY tokenized_prereqs.tsv /app/tokenized_prereqs.tsv
COPY tokenized_prereqs_corrected.tsv /app/tokenized_prereqs_corrected.tsv

# Run the web server
CMD ["java", "-jar", "app.jar"]