# mvn-project-default
## Overview
Maven extension to set up default project settings (repositories)

This project compiles into a Maven extension that can be used to
populate the default project information that is otherwise not
available, i.e. when executing Maven without a project.

The main reason for having this is that it's notoriously complicated
to handle artifacts that are not in the default (Central) repository
unless such repositories are defined in the project file.

However, Maven can be used as quite a convenient vehicle to download
and execute code (using plugins) without much additional set-up, however
if that code must come from a private repository, things don't work
so well (i.e. https://stackoverflow.com/q/68854622/622266).

## Installation

To use: download and install the extension into your Maven installation
extension directory. There is no way to register the extension otherwise,
as that can also only be done using project configuration.

```shell
(
test -z "$MVN_HOME" && MVN_HOME="$(mvn -q org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -DforceStdout -Dexpression=maven.home)";

mvn -X org.apache.maven.plugins:maven-dependency-plugin:3.1.2:copy \
 -Dartifact=codes.vps:mvn-project-default:RELEASE:jar\
 -DoutputDirectory=. \
 -Dmdep.stripClassifier=true -Dmdep.stripVersion=true &&
sudo mkdir -p "$MVN_HOME/lib/ext" && 
sudo mv mvn-project-default.jar "$MVN_HOME/lib/ext/" 
)
```

## How to Use

The extension will look for `~/.m2/default_project.xml` file 
(or, alternatively, the file path specified as a value of `codes.vps.mvn-project-default` system property),
and read it as a standard project file. All repositories or plugin repositories statements specified at the
top level of the file, or for any of the currently active profiles, will be applied to the reactor before executing
the requested operation.

It's not recommended defining the repositories at the top level of the project file, as they will then
apply to all Maven operations, which can have unintended side effects.

## Example

### ~/.m2/default_project.xml
```xml
<?xml version="1.0"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>
    <artifactId>none</artifactId> <!-- required child, not used otherwise -->

    <profiles>
        <profile>
            <id>company.repos</id>

            <repositories>
                <repository>
                    <id>company.repo</id>
                    <name>Company Development Repository</name>
                    <url>https://dev-code.company.com/artifactory/repository</url>
                </repository>
            </repositories>

            <pluginRepositories>
                <pluginRepository>
                    <id>company.repo</id>
                    <name>Company Development Repository</name>
                    <url>https://dev-code.company.com/artifactory/repository</url>
                </pluginRepository>
            </pluginRepositories>

        </profile>
    </profiles>

</project>
```

### Executing Maven to apply repositories:
```shell
mvn -Pcompany.repos com.company:super-plugin:1.14:run-cool-code
```

