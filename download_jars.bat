chcp 65001
@echo off
if not exist libs mkdir libs
cd libs

for %%u in (
    https://repo1.maven.org/maven2/org/apache/commons/commons-collections4/4.5.0/commons-collections4-4.5.0.jar
    https://repo1.maven.org/maven2/org/apache/commons/commons-compress/1.28.0/commons-compress-1.28.0.jar
    https://repo1.maven.org/maven2/commons-io/commons-io/2.21.0/commons-io-2.21.0.jar
    https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.20.0/commons-lang3-3.20.0.jar
    https://repo1.maven.org/maven2/org/freemarker/freemarker/2.3.34/freemarker-2.3.34.jar
    https://repo1.maven.org/maven2/com/github/javaparser/javaparser-core/3.27.1/javaparser-core-3.27.1.jar
    https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.25.2/log4j-api-2.25.2.jar
    https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.25.2/log4j-core-2.25.2.jar
    https://repo1.maven.org/maven2/org/apache/poi/poi/5.5.0/poi-5.5.0.jar
    https://repo1.maven.org/maven2/org/apache/poi/poi-ooxml/5.5.0/poi-ooxml-5.5.0.jar
    https://repo1.maven.org/maven2/org/apache/poi/poi-ooxml-full/5.5.0/poi-ooxml-full-5.5.0.jar
    https://repo1.maven.org/maven2/org/apache/poi/poi-ooxml-schemas/4.1.2/poi-ooxml-schemas-4.1.2.jar
    https://repo1.maven.org/maven2/org/apache/poi/poi-scratchpad/5.5.0/poi-scratchpad-5.5.0.jar
    https://repo1.maven.org/maven2/org/springframework/spring-beans/7.0.0/spring-beans-7.0.0.jar
    https://repo1.maven.org/maven2/org/springframework/spring-core/7.0.0/spring-core-7.0.0.jar
    https://repo1.maven.org/maven2/org/apache/xmlbeans/xmlbeans/5.3.0/xmlbeans-5.3.0.jar
) do (
    echo Downloading %%u
    powershell -Command "Invoke-WebRequest -Uri '%%u' -OutFile (Split-Path '%%u' -Leaf)"
)

echo Done.
pause

