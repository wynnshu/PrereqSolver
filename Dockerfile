# Use a build environment with Maven
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app

# Copy only the pom.xml first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the source code and build
COPY src ./src
RUN mvn package

# Create the runtime image (lighter weight)
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/target/prereqsolver-1.0-SNAPSHOT.jar /app/app.jar

# IMPORTANT: Copy the data file needed by PrereqData
COPY tokenized_prereqs_corrected.tsv /app/tokenized_prereqs_corrected.tsv

# Run the web server
CMD ["java", "-jar", "app.jar"]