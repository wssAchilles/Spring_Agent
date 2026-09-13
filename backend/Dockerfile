# ============================================================================
# Phase 20: 生产级 Spring Boot 3 / Java 21 多阶段容器镜像
# 涵盖: cgroups v1/v2 内存感知 (-XX:MaxRAMPercentage=75.0)、安全非 root 运行
# ============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# 依赖与源码分层复制以最大化利用 Docker 缓存
COPY pom.xml .
COPY qknow-framework ./qknow-framework
COPY qknow-module-ai ./qknow-module-ai
COPY qknow-module-kb ./qknow-module-kb
COPY qknow-module-kmc ./qknow-module-kmc
COPY qknow-module-system ./qknow-module-system
COPY qknow-module-app ./qknow-module-app
COPY qknow-module-dm ./qknow-module-dm
COPY qknow-module-ext ./qknow-module-ext
COPY qknow-module-kg ./qknow-module-kg
COPY qknow-hermes ./qknow-hermes
COPY qknow-server ./qknow-server

# 执行模块编译与打包
RUN mvn clean package -DskipTests -pl qknow-server -am

# ----------------------------------------------------------------------------
# 运行环境阶段 (基于稳定安全的 Ubuntu Jammy OpenJDK 21 运行时)
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 创建非 root 安全运行用户
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 从构建镜像提取打包完成的可执行 JAR
COPY --from=builder /app/qknow-server/target/*.jar app.jar
RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8099

# 容器化 JVM 关键优化参数：
# 1. 动态感知 cgroups 限额，堆内存上限为容器内存的 75%
# 2. 为堆外内存 (DirectByteBuffer/Metaspace/JNI SIMD) 保留 25% 安全余量
# 3. 发生 OOM 时主动退出让编排器自愈重启，杜绝僵尸进程
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
