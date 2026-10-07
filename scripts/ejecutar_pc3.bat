@echo off
REM PaqRap - PC 3: experimento de simulacion del nivel ALTA (Etapa 19.1).
REM Reparto por NIVEL (no por algoritmo): cada PC corre ambos algoritmos de su nivel,
REM para no confundir el efecto del algoritmo con el de la maquina.
REM Datos oficiales: antes de ejecutar,  set VENTAS=datos\ventas2026MM.txt  y  set BLOQUEOS=datos\2026MM.bloqueadas
call "%~dp0ejecutar_nivel.bat" ALTA pc3
