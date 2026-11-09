# 주문 서버 이미지. 빌드 컨텍스트는 저장소 루트다
#   docker build -f order-service/Dockerfile -t cartmate-order-service .
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY common/build.gradle common/
COPY member-service/build.gradle member-service/
COPY product-service/build.gradle product-service/
COPY order-service/build.gradle order-service/
COPY payment-service/build.gradle payment-service/
COPY ai-service/build.gradle ai-service/
COPY etc-service/build.gradle etc-service/
RUN chmod +x gradlew && ./gradlew :order-service:dependencies --no-daemon > /dev/null

COPY common/src common/src
COPY order-service/src/main order-service/src/main
RUN ./gradlew :order-service:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=builder --chown=app:app /app/order-service/build/libs/order-service-*-SNAPSHOT.jar app.jar
USER app

ENV TZ=Asia/Seoul \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul"

EXPOSE 8986
ENTRYPOINT ["java", "-jar", "app.jar"]
