#!/bin/bash
# PaqRap - Inicia la solución integrada (Planificador + Visualizador web).
# Uso:  scripts/iniciar_servidor.sh [puerto]      (por defecto 8080, o la variable PORT)
# Abrir luego  http://<ip-del-servidor>:<puerto>/  desde cualquier dispositivo.
# Trabaja desde la raíz del proyecto: ahí están config/, juego_de_datos/ y web/.
set -e
cd "$(dirname "$0")/.."
[ -f target/classes/pe/edu/pucp/gamesoft/paqrap/ServidorWeb.class ] || scripts/compilar.sh
PUERTO="${1:-${PORT:-8080}}"
exec java -Xmx3g -Dstdout.encoding=UTF-8 -cp target/classes pe.edu.pucp.gamesoft.paqrap.ServidorWeb "$PUERTO"
