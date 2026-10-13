# 太湖调度后端镜像（多阶段自包含构建；基础镜像需可从镜像源获取，
# 构建机若无法访问公网仓库，请预先准备 maven:3.9-eclipse-temurin-17）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY docker-maven-settings.xml /root/.m2/settings.xml
COPY . .
RUN mvn -B -pl ruoyi-admin -am package -DskipTests -q

# 运行阶段：沿用同一基础镜像（内含 JDK17；演示版取舍——避免依赖额外的 JRE 镜像源）
FROM maven:3.9-eclipse-temurin-17
WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Dfile.encoding=UTF-8 -Xms512m -Xmx1024m"
COPY --from=build /src/ruoyi-admin/target/ruoyi-admin.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
