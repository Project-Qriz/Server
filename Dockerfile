FROM openjdk:11-jdk-slim as builder
WORKDIR /app

# 설정 파일을 위한 인자 추가
ARG CONFIG_PATH=.

COPY . .
COPY ${CONFIG_PATH}/application-dev.properties /app/src/main/resources/

# 권한 설정
RUN chmod +x ./gradlew

RUN ./gradlew dependencies

RUN ./gradlew build -x test


FROM openjdk:11-jre-slim
WORKDIR /app

ENV TZ=Asia/Seoul

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-Dspring.profiles.active=dev", "-jar", "app.jar"]