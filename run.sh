#!/usr/bin/env sh

 ./mvnw package -Dmaven.test.skip=true && java -jar target/jira-harvest-0.0.1-SNAPSHOT.jar