#!/bin/bash
# PaqRap - Compila el proyecto sin Maven (solo necesita el JDK 21 o superior).
# Uso, desde cualquier carpeta:  scripts/compilar.sh
# Deja las clases en target/classes, igual que "mvn compile".
set -e
cd "$(dirname "$0")/.."
rm -rf target/classes
mkdir -p target/classes
javac --release 21 -encoding UTF-8 -d target/classes $(find src/main/java -name "*.java")
echo "Compilado en target/classes"
