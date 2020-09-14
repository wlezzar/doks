FROM openjdk:11.0.7-jre-slim

COPY build/install/doks /app

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["/app/bin/doks"]