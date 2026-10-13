# 太湖调度后端镜像（多阶段自包含构建，无需本地 Maven）
# 构建上下文为仓库根目录的 ruoyi-backend/
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY docker-maven-settings.xml /root/.m2/settings.xml
COPY . .
RUN mvn -B -pl ruoyi-admin -am package -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Dfile.encoding=UTF-8 -Xms512m -Xmx1024m"
COPY --from=build /src/ruoyi-admin/target/ruoyi-admin.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
