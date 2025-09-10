# Netgrif Application Engine Plugin Maven Plugin

Maven plugin for building plugins for Netgrif Platform.

- GroupId: `com.netgrif`
- ArtifactId: `nae-plugin-maven-plugin`
- Latest version: `1.0.3`
- Project site: [https://netgrif.github.io/nae-plugin-maven-plugin](https://netgrif.github.io/nae-plugin-maven-plugin)
- Issue
  tracker: [https://github.com/netgrif/nae-plugin-maven-plugin/issues](https://github.com/netgrif/nae-plugin-maven-plugin/issues)
- License: Apache-2.0

## Requirements

- Java 21 (JDK 21) to build and run
- Apache Maven (any current stable version should work)

## Goals

- generate-plugin-registration - generating plugin registration class

## Usage

```xml

<plugin>
    <groupId>com.netgrif</groupId>
    <artifactId>nae-plugin-maven-plugin</artifactId>
    <version>1.0.3</version>
    <configuration>
        <registrationName>examplePlugin</registrationName>
        <apiVersion>1.0.0</apiVersion>
        <componentScanBasePackage>org.example</componentScanBasePackage>
    </configuration>
    <executions>
        <execution>
            <id>generate-plugin-registration</id>
            <goals>
                <goal>generate-plugin-registration</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Invoke a goal

You can run any goal directly with its fully qualified name:

``` bash
mvn com.netgrif:nae-plugin-maven-plugin:1.0.3:generate-plugin-registration
```

## Building from source

``` bash
# Clone
git clone https://github.com/netgrif/nae-plugin-maven-plugin.git
cd nae-plugin-maven-plugin

# Build
mvn -U -DskipTests clean install
```

Prerequisites:

- JDK 21 available on PATH (JAVA_HOME set to your JDK 21)
- Maven installed

## Contributing

Contributions are welcome!

- Read [contribution guidelines](https://github.com/netgrif/nae-plugin-maven-plugin?tab=contributing-ov-file).
- Report
  issues: [https://github.com/netgrif/nae-plugin-maven-plugin/issues](https://github.com/netgrif/nae-plugin-maven-plugin/issues).
- Open pull requests with clear descriptions.
- Please follow standard Maven project conventions and include tests where applicable.

## License

Licensed under the Apache License, Version 2.0.

- License text: [https://www.apache.org/licenses/LICENSE-2.0.txt](https://www.apache.org/licenses/LICENSE-2.0.txt)
