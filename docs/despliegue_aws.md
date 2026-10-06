# Despliegue de la solución integrada (etapa 32)

La solución es **un solo proceso Java** (`ServidorWeb`) que sirve el visualizador web y la API del planificador en el mismo puerto. No necesita base de datos ni dependencias externas: solo **Java 21** y la carpeta del proyecto, con `config/`, `juego_de_datos/` y `web/`.

## 1. En una instancia AWS EC2 (recomendado)

1. **Crear la instancia:**
   - Amazon Linux 2023 o Ubuntu 22.04/24.04.
   - Tipo `t3.large` (2 vCPU, 8 GB) o mayor. El planificador usa 2 s de CPU por replanificación, y con los tres escenarios a la vez conviene tener 2 núcleos o más.
2. **Grupo de seguridad:** abrir el puerto **8080** (TCP) para `0.0.0.0/0` o, si se prefiere, solo para las IP del aula. El 22 queda para SSH.
3. **Instalar Java 21 y git:**
   ```bash
   sudo dnf install -y java-21-amazon-corretto-devel git      # Amazon Linux
   # sudo apt install -y openjdk-21-jdk git                    # Ubuntu
   ```
4. **Traer el proyecto.** Puede ser con `git clone` del repositorio (rama `feature/JL` o `main`) o subiendo y descomprimiendo el ZIP del entregable:
   ```bash
   git clone https://github.com/JohnArzapalo/PaqRap.git && cd PaqRap
   # o:  unzip PaqRap_sol_integrada_sem08.zip && cd PaqRap
   chmod +x scripts/*.sh
   ```
5. **Compilar y probar a mano.** Basta con el JDK; no hace falta Maven.
   ```bash
   scripts/compilar.sh
   scripts/iniciar_servidor.sh 8080
   ```
   Abrir `http://<IP-pública>:8080/` desde cualquier dispositivo. Con `Ctrl+C` se detiene.
6. **Dejarlo como servicio**, para que siga corriendo al cerrar SSH y arranque solo:
   ```bash
   sudo cp scripts/paqrap.service /etc/systemd/system/
   # Si el usuario o la carpeta no son ec2-user y /home/ec2-user/PaqRap, editar el archivo
   sudo systemctl daemon-reload && sudo systemctl enable --now paqrap
   journalctl -u paqrap -f
   ```
7. **Actualizar** después de un `git pull`:
   ```bash
   scripts/compilar.sh && sudo systemctl restart paqrap
   ```

> Si se quiere usar el puerto 80 (sin `:8080` en la dirección):
> `sudo iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 8080`
> y abrir el 80 en el grupo de seguridad.

## 2. En una PC del laboratorio (Windows)

```bat
scripts\iniciar_servidor.bat 8080
```

Desde otro dispositivo de la misma red, abrir `http://<IP-de-la-PC>:8080/`. Si no carga, hay que permitir Java en el firewall de Windows.

## 3. ¿Y Vercel?

Vercel aloja páginas estáticas y funciones cortas. **No puede ejecutar el planificador**, porque es un proceso Java que corre de 30 a 60 minutos con su estado en memoria. Si se quiere el visualizador en Vercel:

1. Publicar la carpeta `web/` en Vercel como sitio estático.
2. Abrirlo con la dirección del planificador: `https://<proyecto>.vercel.app/?api=https://<servidor-aws>`.
3. **Requisito:** el planificador debe servirse por **HTTPS**, porque el navegador bloquea las llamadas HTTP desde una página HTTPS. Para eso se necesita un dominio con certificado, por ejemplo con Caddy o Nginx más Let's Encrypt delante del puerto 8080.

Por eso **lo recomendado para esta entrega es servir todo desde la instancia AWS** (paso 1). Es un solo componente desplegado, sin problemas de HTTPS ni de CORS.

## 4. Verificación rápida

```bash
curl http://localhost:8080/api/datos       # {"desde":"2026-01","hasta":"2028-12"}
curl http://localhost:8080/api/corridas    # estado de los tres escenarios
```

En el visualizador:
1. Elegir **Simulación 5 días**, luego **Iniciar**, y dejar la fecha sugerida (16/09/2026). Es una ventana de los datos oficiales que termina sin colapso.
2. Abrir la misma dirección en un celular: se ve la misma corrida.

## 5. Parámetros (`config/parametros.properties`)

| Clave | Por defecto | Qué controla |
|---|---|---|
| `web.puerto` | 8080 | Puerto, si no se indica en la línea de comandos ni en `PORT` |
| `web.carpeta` | `web` | Carpeta del visualizador |
| `web.carpeta_ventas`, `web.carpeta_bloqueos`, `web.mantenimiento` | `juego_de_datos/...` | Datos oficiales |
| `web.diaadia_dias` | 30 | Días que dura la operación día a día antes de cerrarse sola |
| `web.factor_colapso` | 600 | Ritmo por defecto del escenario hasta el colapso (minutos simulados por minuto real) |
| `sim5d.minutos_reales` | 30 | Minutos reales que toma la simulación de 5 días |
| `simulacion.ta_ms` | 2000 | Presupuesto Ta de cada replanificación |
