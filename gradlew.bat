@ECHO OFF
SET DIR=%~dp0
SET JAVA_CMD=java

%JAVA_CMD% -jar "%DIR%\gradle\wrapper\gradle-wrapper.jar" %*
