FROM ubuntu
WORKDIR /app
RUN apt-get update && apt-get install \
    openjdk-11-jdk \
    maven \
     && rm -rf /var/lib/apt/lists/*
WORKDIR /app/game
RUN mvn install
RUN mv target/game-1.0-SNAPSHOT.jar game.jar
CMD ["java", "-jar", "game.jar"]