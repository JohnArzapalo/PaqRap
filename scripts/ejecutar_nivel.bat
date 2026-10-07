@echo off
REM ======================================================================
REM  PaqRap - Ejecuta el experimento de simulacion para UN nivel de carga
REM  (Etapas 19.1 y 25). Lo llaman ejecutar_pc1/2/3.bat; no se usa directamente.
REM
REM  Uso: scripts\ejecutar_nivel.bat NIVEL ETIQUETA_PC
REM  Trabaja desde la raiz del proyecto y deja los resultados en salidas\.
REM  Variables opcionales (defina antes de llamar para usar datos oficiales):
REM    VENTAS    archivo de pedidos     (por defecto el sintetico del mes)
REM    BLOQUEOS  archivo de bloqueos    (por defecto el sintetico del mes)
REM    REPLICAS  replicas (situaciones) por nivel; cada una la corren TABU y AG
REM              (por defecto 40; ver analisis\potencia.py y docs\protocolo_experimento.md)
REM    ESCENARIO SIM_5D (por defecto: variable principal, %% de colapsos en 5 dias)
REM              o COLAPSO (hasta el colapso: tiempo hasta el colapso)
REM    HILOS     corridas simultaneas (por defecto 1; con Ta por tiempo, a lo sumo
REM              el numero de nucleos FISICOS de la PC)
REM    EXTRA     argumentos adicionales (p. ej. --calibrar-evaluaciones si)
REM ======================================================================
setlocal
REM El script esta en scripts\; las rutas relativas son desde la raiz del proyecto
cd /d "%~dp0.."
if not exist salidas mkdir salidas
set NIVEL=%1
set ETIQUETA=%2
if "%NIVEL%"=="" ( echo Falta el nivel & exit /b 1 )
if "%VENTAS%"=="" set VENTAS=datos\ventas202609_SINTETICO_MES.txt
if "%BLOQUEOS%"=="" set BLOQUEOS=datos\202609_SINTETICO.bloqueadas
if "%REPLICAS%"=="" set REPLICAS=40
if "%ESCENARIO%"=="" set ESCENARIO=SIM_5D
if "%HILOS%"=="" set HILOS=1

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
REM    SIM_5D sin esperar al reloj (--acelerado si); una situacion de pedidos por replica
set SALIDA=salidas\resultados_%COMPUTERNAME%_%ETIQUETA%_%ESCENARIO%_%NIVEL%.csv
echo PC %COMPUTERNAME% (%ETIQUETA%), escenario %ESCENARIO%, nivel %NIVEL%, %REPLICAS% replicas, %HILOS% hilo(s), salida %SALIDA%
java -Xmx8g -Dstdout.encoding=UTF-8 -cp target\classes pe.edu.pucp.gamesoft.paqrap.Experimento --modo simulacion --escenario %ESCENARIO% --acelerado si --situaciones por_replica --archivo "%VENTAS%" --bloqueos "%BLOQUEOS%" --niveles %NIVEL% --replicas %REPLICAS% --hilos %HILOS% --salida %SALIDA% %EXTRA%
if errorlevel 1 ( echo ERROR en la ejecucion & exit /b 1 )

REM 4. Hash independiente (certutil) como verificacion adicional
certutil -hashfile "%VENTAS%" SHA256 > salidas\resultados_%COMPUTERNAME%_%ETIQUETA%_%ESCENARIO%_%NIVEL%_certutil.txt
certutil -hashfile "%BLOQUEOS%" SHA256 >> salidas\resultados_%COMPUTERNAME%_%ETIQUETA%_%ESCENARIO%_%NIVEL%_certutil.txt
echo Listo: %SALIDA%
endlocal
