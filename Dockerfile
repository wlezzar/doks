FROM openjdk:11.0.7-jre-slim

COPY build/install/module-storage-ads /app

EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["/app/bin/module-storage-ads"]