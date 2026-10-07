@echo off
REM PaqRap - Inicia la solucion integrada (Planificador + Visualizador web) en Windows.
REM Uso: scripts\iniciar_servidor.bat [puerto]   (por defecto 8080)
REM Abrir luego http://localhost:8080/ (o la IP de esta PC desde otro dispositivo).
setlocal
cd /d "%~dp0.."
set PUERTO=%1
if "%PUERTO%"=="" set PUERTO=8080
if not exist "target\classes\pe\edu\pucp\gamesoft\paqrap\ServidorWeb.class" (
    echo Compilando...
    if not exist target\classes mkdir target\classes
    dir /s /b src\main\java\*.java > target\fuentes.txt
    javac --release 21 -encoding UTF-8 -d target\classes @target\fuentes.txt
    if errorlevel 1 ( echo ERROR: no compilo & exit /b 1 )
)
java -Xmx3g -Dstdout.encoding=UTF-8 -cp target\classes pe.edu.pucp.gamesoft.paqrap.ServidorWeb %PUERTO%
endlocal
