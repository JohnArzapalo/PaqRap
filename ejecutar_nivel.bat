@echo off
REM ======================================================================
REM  PaqRap - Ejecuta el experimento de simulacion para UN nivel de carga
REM  (Etapa 19.1). Lo llaman ejecutar_pc1/2/3.bat; no se usa directamente.
REM
REM  Uso: ejecutar_nivel.bat NIVEL ETIQUETA_PC
REM  Variables opcionales (defina antes de llamar para usar datos oficiales):
REM    VENTAS   archivo de pedidos     (por defecto el sintetico del mes)
REM    BLOQUEOS archivo de bloqueos    (por defecto el sintetico del mes)
REM    REPLICAS replicas por algoritmo (por defecto 5)
REM    EXTRA    argumentos adicionales (p. ej. --calibrar-evaluaciones si)
REM ======================================================================
setlocal
cd /d "%~dp0"
set NIVEL=%1
set ETIQUETA=%2
if "%NIVEL%"=="" ( echo Falta el nivel & exit /b 1 )
if "%VENTAS%"=="" set VENTAS=datos\ventas202609_SINTETICO_MES.txt
if "%BLOQUEOS%"=="" set BLOQUEOS=datos\202609_SINTETICO.bloqueadas
if "%REPLICAS%"=="" set REPLICAS=5

REM 1. Compilar si hace falta
if not exist "target\classes\pe\edu\pucp\gamesoft\paqrap\Experimento.class" (
    echo Compilando...
    if exist "C:\Program Files\Maven\apache-maven-3.9.16\bin\mvn.cmd" (
        call "C:\Program Files\Maven\apache-maven-3.9.16\bin\mvn.cmd" -o -q compile
    ) else (
        call mvn -o -q compile
    )
    if errorlevel 1 ( echo ERROR: no compilo & exit /b 1 )
)

REM 2. Verificar que existen los archivos de entrada y validarlos
if not exist "%VENTAS%" ( echo ERROR: no existe %VENTAS% & exit /b 1 )
if not exist "%BLOQUEOS%" ( echo ERROR: no existe %BLOQUEOS% & exit /b 1 )
java -cp target\classes pe.edu.pucp.gamesoft.paqrap.ValidadorEntradas --ventas "%VENTAS%" --bloqueos "%BLOQUEOS%"
if errorlevel 1 ( echo ERROR: los archivos de entrada tienen errores, revise el informe & exit /b 1 )

REM 3. Ejecutar (el programa registra los SHA-256 de las entradas en *_hashes.txt)
set SALIDA=resultados_%COMPUTERNAME%_%ETIQUETA%_%NIVEL%.csv
echo PC %COMPUTERNAME% (%ETIQUETA%), nivel %NIVEL%, salida %SALIDA%
java -Dstdout.encoding=UTF-8 -cp target\classes pe.edu.pucp.gamesoft.paqrap.Experimento --modo simulacion --archivo "%VENTAS%" --bloqueos "%BLOQUEOS%" --niveles %NIVEL% --replicas %REPLICAS% --salida %SALIDA% %EXTRA%
if errorlevel 1 ( echo ERROR en la ejecucion & exit /b 1 )

REM 4. Hash independiente (certutil) como verificacion adicional
certutil -hashfile "%VENTAS%" SHA256 > resultados_%COMPUTERNAME%_%ETIQUETA%_%NIVEL%_certutil.txt
certutil -hashfile "%BLOQUEOS%" SHA256 >> resultados_%COMPUTERNAME%_%ETIQUETA%_%NIVEL%_certutil.txt
echo Listo: %SALIDA%
endlocal
