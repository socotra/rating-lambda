# Rating Lambda Deployment Guide

This guide outlines all the steps to set up and deploy the `rating-lambda` project as an AWS Lambda function using a JAR file.

## Overview

This Java Lambda function rates a personal auto insurance quote using the Socotra SDK. The goal is to:

1. Install local JAR dependencies manually.
2. Set up IntelliJ with correct SDK and library references.
3. Modify the `pom.xml` to build a fat JAR using the Maven Shade Plugin.
4. Deploy to AWS Lambda via the AWS console.

## 1. Install External JARs Manually with Maven

If the project depends on custom/local JARs not published in a Maven repository, use the following command to install each one locally:

```bash
mvn install:install-file -Dfile=libs/<jar-file> \
                         -DgroupId=com.socotra.custom \
                         -DartifactId=<artifact-name> \
                         -Dversion=<version> \
                         -Dpackaging=jar
```

### Example commands

```bash
# proto-lib-v1.0.5.jar
mvn install:install-file -Dfile=libs/proto-lib-v1.0.5.jar \
                         -DgroupId=com.socotra.custom \
                         -DartifactId=proto-lib \
                         -Dversion=1.0.5 \
                         -Dpackaging=jar

# core-datamodel-v1.6.169.jar
mvn install:install-file -Dfile=libs/core-datamodel-v1.6.169.jar \
                         -DgroupId=com.socotra.custom \
                         -DartifactId=core-datamodel \
                         -Dversion=1.6.169 \
                         -Dpackaging=jar

# customer-config-source.jar
mvn install:install-file -Dfile=libs/customer-config-source.jar \
                         -DgroupId=com.socotra.custom \
                         -DartifactId=customer-config-source \
                         -Dversion=1.0.0 \
                         -Dpackaging=jar

# customer-config.jar
mvn install:install-file -Dfile=libs/customer-config.jar \
                         -DgroupId=com.socotra.custom \
                         -DartifactId=customer-config \
                         -Dversion=1.0.0 \
                         -Dpackaging=jar

# jackson-core-2.15.3.jar
mvn install:install-file -Dfile=libs/jackson-core-2.15.3.jar \
                         -DgroupId=com.fasterxml.jackson.core \
                         -DartifactId=jackson-core \
                         -Dversion=2.15.3 \
                         -Dpackaging=jar

# jackson-datatype-jsr310-2.15.3.jar
mvn install:install-file -Dfile=libs/jackson-datatype-jsr310-2.15.3.jar \
                         -DgroupId=com.fasterxml.jackson.datatype \
                         -DartifactId=jackson-datatype-jsr310 \
                         -Dversion=2.15.3 \
                         -Dpackaging=jar

# protobuf-java-3.25.1.jar
mvn install:install-file -Dfile=libs/protobuf-java-3.25.1.jar \
                         -DgroupId=com.google.protobuf \
                         -DartifactId=protobuf-java \
                         -Dversion=3.25.1 \
                         -Dpackaging=jar

# slf4j-api-1.7.36.jar
mvn install:install-file -Dfile=libs/slf4j-api-1.7.36.jar \
                         -DgroupId=org.slf4j \
                         -DartifactId=slf4j-api \
                         -Dversion=1.7.36 \
                         -Dpackaging=jar

# aws-lambda-java-core-1.0.0.jar
mvn install:install-file -Dfile=libs/aws-lambda-java-core-1.0.0.jar \
                         -DgroupId=com.amazonaws \
                         -DartifactId=aws-lambda-java-core \
                         -Dversion=1.0.0 \
                         -Dpackaging=jar

# aws-java-sdk-1.12.786.jar
mvn install:install-file -Dfile=libs/aws-java-sdk-1.12.786.jar \
                         -DgroupId=com.amazonaws \
                         -DartifactId=aws-java-sdk \
                         -Dversion=1.12.786 \
                         -Dpackaging=jar
```


## 2. Update `pom.xml`

Add the manually installed dependencies under `<dependencies>`:

```xml
<dependencies>
  <dependency>
    <groupId>com.socotra.custom</groupId>
    <artifactId>proto-lib</artifactId>
    <version>1.0.5</version>
  </dependency>

  <dependency>
    <groupId>com.socotra.custom</groupId>
    <artifactId>core-datamodel</artifactId>
    <version>1.6.169</version>
  </dependency>

  <dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-lambda-java-core</artifactId>
    <version>1.0.0</version>
  </dependency>

/// add the rest below
</dependencies>
```

Add the Maven Shade Plugin to bundle all dependencies into one fat JAR:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-shade-plugin</artifactId>
      <version>3.4.1</version>
      <executions>
        <execution>
          <phase>package</phase>
          <goals>
            <goal>shade</goal>
          </goals>
          <configuration>
            <createDependencyReducedPom>false</createDependencyReducedPom>
            <transformers>
              <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                <mainClass>com.socotra.lambda.RatingLambdaHandler</mainClass>
              </transformer>
            </transformers>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

## 3. Configure IntelliJ

Open Project Structure (`Cmd + ;`):

- Go to Modules > Dependencies tab.
- Ensure all jars from your `libs/` folder are added.
- Confirm Module SDK is set to Java 21 (e.g. Eclipse Temurin 21).

## 4. Build the JAR

In the terminal:

```bash
mvn clean package
```

After a successful build, you’ll get:

```bash
target/rating-lambda-<version>.jar
```

This is the fat JAR you will upload to AWS Lambda.

## 5. Deploy to AWS Lambda

1. Open AWS Lambda Console.
2. Click Create function > Author from scratch.
3. Use:
   - Name: `rating-lambda`
   - Runtime: `Java 21`
4. Click Create function.
5. In Function Code, select Upload from > .zip or .jar file.
6. Upload the `target/rating-lambda-<version>.jar`.
7. In Runtime settings, set:
   - Handler: `com.socotra.lambda.RatingLambdaHandler::handleRequest`
8. Click Save.
9. Use the Test tab to trigger the function with test JSON input.

## 6. Sample Test Payload

```json
{
  "quote": {
    "locator": "01HZP2HV2F3K4PQB27TZ9DZR5T",
    "personalVehicles": [
      {
        "data": {
          "value": 120000,
          "vin": "VIN1111"
        },
        "fire": {
          "PADeductible": 6,
          "PALimit": 2
        }
      }
    ]
  },
  "duration": 6
}
```

## Notes

- If your function uses external logging libraries like SLF4J, make sure the corresponding JAR is added to both `libs/` and Maven.
- If you get Jackson-related deserialization errors, ensure all record and field types used in your JSON have accessible constructors or valid setters/getters.

## Project Structure

```
rating-lambda/
├── pom.xml
├── libs/
│   └── [all your JARs]
├── src/
│   └── main/
│       └── java/
│           └── com/socotra/lambda/RatingLambdaHandler.java
```

## Done

You now have a working AWS Lambda function for rating insurance quotes.
