# mvn-project-default
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

To use: download and install the extension into your Maven installation
extension directory. There is no way to register the extension otherwise,
as that can also only be done using project configuration.

```shell
(
test -z "$MVN_HOME" && MVN_HOME="$(type -p mvn)/..bin"
mvn -X org.apache.maven.plugins:maven-dependency-plugin:3.1.2:copy \
-Dartifact=codes.vps:mvn-project-default:RELEASE:jar\
-Dmdep.stripClassifier=true -Dmdep.stripVersion=true
sudo mv mvn-project-default.jar "$MVN_HOME/lib/ext" 
)
```

