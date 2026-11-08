@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@
@rem      https://www.apache.org/licenses/LICENSE-2.0
@
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="true" (
@echo off
@rem set local
)

@rem ========== MAIN SCRIPT ===========================================================

@echo off

@rem Specify the JVM to use via JAVA_HOME, or specify the full path to the java command.
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set JAVA_EXE=%JAVA_HOME%\bin\java.exe
    ) else (
        echo Did not find java.exe under JAVA_HOME: %JAVA_HOME%
        echo Please set JAVA_HOME to point to a valid JDK installation.
        exit /b 1
    )
) else (
    set JAVA_EXE=java.exe
)

@rem Collect all arguments for the java command, stacking in reverse order:
@rem   * args from the command line
@rem   * the main class name
@rem   * -classpath
@rem   * -D...appname settings
@rem   * --module-path (only if needed)
@rem   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.

set DEFAULT_JVM_OPTS=-Xmx64m -Xms64m

@rem Find java.exe
if not defined JAVA_HOME (
    for /f "tokens=1* delims==" %%i in ('where java 2^>nul') do (
        if /i "%%i"=="java.exe" (
            set JAVA_EXE=%%j
        )
    )
)

if not defined JAVA_EXE (
    echo Could not find java.exe
    echo Please set JAVA_HOME to a valid JDK installation.
    exit /b 1
)

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% ^
    -classpath "%~dp0gradle\wrapper\gradle-wrapper.jar" ^
    org.gradle.wrapper.GradleWrapperMain ^
    %*

:end
