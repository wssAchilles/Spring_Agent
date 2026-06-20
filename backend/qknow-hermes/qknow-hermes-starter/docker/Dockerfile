FROM eclipse-temurin:17-jre
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
WORKDIR /usr/app/jar
COPY ./target/*.jar ./hermes.jar
EXPOSE 9090 8081
ENTRYPOINT ["java", "-Xms512m", "-Xmx2g", "-jar", "hermes.jar"]
