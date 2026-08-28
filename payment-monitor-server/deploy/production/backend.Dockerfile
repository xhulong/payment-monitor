FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -ntp -Pprod -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 payment \
    && mkdir -p /app/logs /ruoyi/server/temp \
    && chown -R payment:payment /app /ruoyi
COPY --from=build /workspace/ruoyi-admin/target/ruoyi-admin.jar /app/app.jar
USER payment
ENV TZ=UTC JAVA_OPTS="-XX:+UseZGC -XX:+HeapDumpOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
