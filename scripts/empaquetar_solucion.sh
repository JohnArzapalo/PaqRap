#!/bin/bash
# PaqRap - Genera el ZIP del entregable "solución integrada a la fecha".
# Uso:  scripts/empaquetar_solucion.sh [carpeta] [nombre]
#   por defecto: sol.integrada.sem08/PaqRap_sol_integrada_sem08.zip
# Empaqueta EXACTAMENTE lo que está en el último commit (git archive): código, datos
# oficiales, visualizador, scripts y documentación. Haga commit antes de empaquetar.
# Los archivos marcados con export-ignore en .gitattributes no se incluyen.
set -e
cd "$(dirname "$0")/.."
CARPETA="${1:-sol.integrada.sem08}"
NOMBRE="${2:-PaqRap_sol_integrada_sem08.zip}"
if ! git diff --quiet HEAD -- . ":(exclude)$CARPETA"; then
    echo "AVISO: hay cambios sin commit; el ZIP solo incluye lo del último commit ($(git rev-parse --short HEAD))."
fi
mkdir -p "$CARPETA"
git archive --format=zip --prefix=PaqRap/ -o "$CARPETA/$NOMBRE" HEAD -- . ":(exclude)$CARPETA"
echo "Listo: $CARPETA/$NOMBRE (commit $(git rev-parse --short HEAD), $(du -h "$CARPETA/$NOMBRE" | cut -f1))"
