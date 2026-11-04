# Despliegue de la solución integrada (etapa 32)

La solución es **un solo proceso Java** (`ServidorWeb`) que sirve el visualizador web y la API del planificador en el mismo puerto. No necesita base de datos ni dependencias externas: solo **Java 21** y la carpeta del proyecto, con `config/`, `juego_de_datos/` y `web/`.

## 1. En una instancia AWS EC2 (respaldo)

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

## 2. En la máquina virtual del curso (vigente desde el 21-oct-2026)

La sección de Ingeniería Informática asignó al equipo una máquina virtual: **http://1inf54-982-5e.inf.pucp.edu.pe/** (200.16.7.154), con el usuario SSH `1inf54.982.5e`. La contraseña la tiene el equipo; no se escribe en el repositorio. Tiene Ubuntu 24.04, 2 núcleos, 3.8 GB de memoria y OpenJDK 21.

**Lo que cambia respecto de AWS:**
- Solo están abiertos los puertos **22, 80 y 443**, y **Tomcat ocupa el 8080**. Por eso PaqRap corre en el **8090** y **nginx** (que ya escucha en el 80) le pasa las peticiones.
- La memoria disponible es de unos 2.6 GB, así que el servidor se arranca con `-Xmx1500m` en vez de `-Xmx3g`.
- El proyecto se copia como carpeta (`tar.gz`), sin git.

**Versiones desplegadas:**

| Fecha | Versión | Cómo se hizo |
|---|---|---|
| 21-oct-2026 | Etapa 34 (commit `7bdde4f`) | Instalación (abajo) |
| 28-oct-2026 | Etapa 35 (`main` en `d9674a6`) | Actualización (abajo). Se comprobó desde fuera que la página servida trae las funciones de la etapa 35 y que la API responde |

**Instalación (hecha el 21-oct-2026):**
1. En la PC, generar el paquete desde el último commit. Los archivos internos del repositorio marcados con `export-ignore` en `.gitattributes` quedan fuera solos; además se excluyen los ZIP de entregas anteriores:
   ```bash
   git archive --format=tar.gz --prefix=PaqRap/ -o PaqRap_vm.tar.gz HEAD -- . ":(exclude)sol.integrada.sem08" ":(exclude)sol.integrada.sem10"
   ```
2. Copiarlo a la máquina, junto con el servicio y la configuración de nginx de abajo, y entrar:
   ```bash
   scp PaqRap_vm.tar.gz paqrap.service paqrap.nginx 1inf54.982.5e@1inf54-982-5e.inf.pucp.edu.pe:~
   ssh 1inf54.982.5e@1inf54-982-5e.inf.pucp.edu.pe
   ```
3. En la máquina, descomprimir, bajar la memoria y compilar:
   ```bash
   tar -xzf ~/PaqRap_vm.tar.gz -C ~ && cd ~/PaqRap && chmod +x scripts/*.sh
   sed -i 's/-Xmx3g/-Xmx1500m/' scripts/iniciar_servidor.sh
   scripts/compilar.sh
   ```
4. Instalar el servicio y nginx:
   ```bash
   sudo cp ~/paqrap.service /etc/systemd/system/ && sudo systemctl daemon-reload && sudo systemctl enable --now paqrap
   sudo cp ~/paqrap.nginx /etc/nginx/sites-available/paqrap && sudo ln -sf /etc/nginx/sites-available/paqrap /etc/nginx/sites-enabled/paqrap
   sudo rm -f /etc/nginx/sites-enabled/default && sudo nginx -t && sudo systemctl reload nginx
   ```

`paqrap.service`:
```ini
[Unit]
Description=PaqRap - Planificador y Visualizador web
After=network.target

[Service]
User=1inf54.982.5e
WorkingDirectory=/home/1inf54.982.5e/PaqRap
ExecStart=/home/1inf54.982.5e/PaqRap/scripts/iniciar_servidor.sh 8090
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

`paqrap.nginx`:
```nginx
server {
    listen 80;
    server_name 1inf54-982-5e.inf.pucp.edu.pe 200.16.7.154;
    location / {
        proxy_pass http://127.0.0.1:8090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 120s;
    }
}
```

**Actualizar:**
1. Generar el paquete nuevo y copiarlo (pasos 1 y 2).
2. En la máquina, ejecutar:
   ```bash
   tar -xzf ~/PaqRap_vm.tar.gz -C ~ && cd ~/PaqRap && chmod +x scripts/*.sh && sed -i 's/-Xmx3g/-Xmx1500m/' scripts/iniciar_servidor.sh && scripts/compilar.sh && sudo systemctl restart paqrap
   ```
   El `sed` hay que repetirlo, porque el paquete trae `iniciar_servidor.sh` con `-Xmx3g`. El reinicio corta las corridas en curso.

**Avisos que no son errores:** `sudo: unable to resolve host 1inf54-982-5e` aparece porque la máquina no tiene su nombre en `/etc/hosts`. Se quita con `echo "127.0.1.1 1inf54-982-5e" | sudo tee -a /etc/hosts`.

**Diagnóstico:**
- `sudo systemctl status paqrap --no-pager` debe decir `active (running)`.
- La salida del servidor se ve con `journalctl -u paqrap -n 50 --no-pager`.
- Si aparece la página de nginx en vez de PaqRap, falta borrar el sitio `default`. Si sale «502 Bad Gateway», PaqRap no está corriendo.

## 3. En una PC del laboratorio (Windows)

```bat
scripts\iniciar_servidor.bat 8080
```

Desde otro dispositivo de la misma red, abrir `http://<IP-de-la-PC>:8080/`. Si no carga, hay que permitir Java en el firewall de Windows.

## 4. ¿Y Vercel?

Vercel aloja páginas estáticas y funciones cortas. **No puede ejecutar el planificador**, porque es un proceso Java que corre de 30 a 60 minutos con su estado en memoria. Si se quiere el visualizador en Vercel:

1. Publicar la carpeta `web/` en Vercel como sitio estático.
2. Abrirlo con la dirección del planificador: `https://<proyecto>.vercel.app/?api=https://<servidor-aws>`.
3. **Requisito:** el planificador debe servirse por **HTTPS**, porque el navegador bloquea las llamadas HTTP desde una página HTTPS. Para eso se necesita un dominio con certificado, por ejemplo con Caddy o Nginx más Let's Encrypt delante del puerto 8080.

Por eso **lo recomendado para esta entrega es servir todo desde la instancia AWS** (paso 1). Es un solo componente desplegado, sin problemas de HTTPS ni de CORS.

## 5. Verificación rápida

```bash
curl http://localhost:8080/api/datos       # {"desde":"2026-01","hasta":"2028-12"}
curl http://localhost:8080/api/corridas    # estado de los tres escenarios
```

En el visualizador:
1. Elegir **Simulación 5 días**, luego **Iniciar**, y dejar la fecha sugerida (16/09/2026). Es una ventana de los datos oficiales que termina sin colapso.
2. Abrir la misma dirección en un celular: se ve la misma corrida.

## 6. Parámetros (`config/parametros.properties`)

| Clave | Por defecto | Qué controla |
|---|---|---|
| `web.puerto` | 8080 | Puerto, si no se indica en la línea de comandos ni en `PORT` |
| `web.carpeta` | `web` | Carpeta del visualizador |
| `web.carpeta_ventas`, `web.carpeta_bloqueos`, `web.mantenimiento` | `juego_de_datos/...` | Datos oficiales |
| `web.diaadia_dias` | 30 | Días que dura la operación día a día antes de cerrarse sola |
| `web.factor_colapso` | 600 | Ritmo por defecto del escenario hasta el colapso (minutos simulados por minuto real) |
| `sim5d.minutos_reales` | 60 | Minutos reales que toma la simulación de 5 días |
| `simulacion.ta_ms` | 2000 | Presupuesto Ta de cada replanificación |
