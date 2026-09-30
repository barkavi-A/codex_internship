@echo off
setlocal
if "%JAVA_HOME%"=="" set JAVA_HOME=C:\Program Files\Java\jdk-17
if exist "%~dp0..\tools\apache-maven-3.9.6\bin\mvn.cmd" (
    "%~dp0..\tools\apache-maven-3.9.6\bin\mvn.cmd" %*
) else (
    mvn %*
)
