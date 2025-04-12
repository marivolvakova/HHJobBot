FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.authors="rusguardian.ru"

# Установка временной зоны и базовых пакетов
RUN apk update && \
    apk add --no-cache tzdata && \
    ln -sf /usr/share/zoneinfo/Europe/Moscow /etc/localtime && \
    echo "Europe/Moscow" > /etc/timezone && \
    rm -rf /var/cache/apk/*

# Установка Node.js и redoc-cli
RUN apk add --no-cache nodejs npm && \
    npm install -g redoc-cli

# Копируем jar с зависимостями
COPY JobSearchBot-1.0-jar-with-dependencies.jar /app.jar

# Запуск
CMD ["sh", "-c", "java ${JAVA_OPTS} -jar /app.jar ${@}"]

