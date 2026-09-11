README — Servidor API PRICE (api_rest)
Requisitos
Python 3.10 o superior instalado en la laptop.
Conexión a Internet solo la primera vez (para instalar dependencias).
Android Studio (para compilar y ejecutar la app).
1. Instalación y arranque del servidor
Abrir la carpeta api_rest.
Ejecutar INICIAR_API.bat (Windows) o iniciar_api.sh (Mac/Linux).
La primera vez crea un entorno virtual (.venv) e instala automáticamente lo que pide requirements.txt: reportlab, pypdf y sus dependencias (pillow, charset-normalizer).
Dejar esa ventana de consola abierta mientras se usa la app — si se cierra, el servidor se apaga.
2. Verificar que quedó activo

En el navegador de la misma laptop, abrir:

http://127.0.0.1:8000/api/health

Debe responder {"status":"ok","api":"PRICE","database":"sqlite"}.

3. Cuentas de práctica
Usuario	Contraseña	Rol
admin	Price2026!	ADMIN
fiscalizador	Price2026!	FISCALIZADOR
consulta	Price2026!	CONSULTA
4. Probar en el emulador de Android Studio

En el campo "URL de la API" de la app:

http://10.0.2.2:8000/

Dirección fija — es un alias especial del emulador para "la laptop que lo contiene", no cambia aunque la laptop cambie de red Wi-Fi.

5. Probar en un celular físico
5.1. Activar la depuración USB en el celular
En el celular: Configuración → Acerca del teléfono → tocar 7 veces seguidas sobre "Número de compilación" (o "Build number"). Aparece el aviso "Ya eres desarrollador".
Volver a Configuración → ahora aparece "Opciones de desarrollador" → entrar y activar "Depuración USB".
5.2. Conectar el celular y ejecutar la app desde Android Studio
Conectar el celular a la laptop con un cable USB.
En el celular aparece un aviso: "¿Permitir depuración USB desde esta computadora?" → aceptar (opcionalmente marcar "confiar siempre en esta computadora" para no repetir el aviso).
En Android Studio, en el selector de dispositivos (donde antes aparecía el emulador), ahora aparece el nombre del celular real → seleccionarlo y pulsar Run. Con esto la app queda instalada y corriendo en el teléfono físico.
5.3. Conectar la app al servidor por Wi-Fi (usando la IP de la laptop)

El celular no entiende 10.0.2.2 ni 127.0.0.1 referidos a la laptop; necesita la IP real de la laptop dentro de la red Wi-Fi.

En la laptop: Configuración → Red e Internet → Wi-Fi → (nombre de tu red) y anotar el valor de "Dirección IPv4".
Ejemplo real usado en este proyecto: red MERCUSYS_C736, IPv4 192.168.1.104.
Conectar el celular a esa misma red Wi-Fi (no datos móviles — si están en redes distintas, nunca se van a ver, sin importar que sigan unidos por el cable USB).
En el campo "URL de la API" de la app, reemplazar la dirección por la de la laptop:
   http://192.168.1.104:8000/

(cambiar 192.168.1.104 por la IP que arroje el paso 1 — puede cambiar si la laptop se reconecta a la red o a otra red distinta). 4. Si no conecta: revisar que Windows Firewall haya permitido el acceso de Python a redes privadas (la primera vez que se ejecuta INICIAR_API.bat, Windows suele preguntarlo).

5.4. Alternativa: solo por cable USB, sin usar Wi-Fi

Con la depuración USB ya activa (paso 5.1) y el celular conectado por cable, en una terminal de la laptop:

adb reverse tcp:8000 tcp:8000

Y en la app usar http://127.0.0.1:8000/ en vez de la IP de la red. Con esto el celular no necesita estar en la misma Wi-Fi — el propio cable USB reenvía el puerto 8000 de la laptop hacia el teléfono.

Notas
Depuración USB → necesaria para instalar/ejecutar la app en el celular desde Android Studio. No tiene relación con la conexión al servidor.
10.0.2.2 → solo emulador, siempre igual.
127.0.0.1 → solo para probar el servidor desde el navegador de la misma laptop, nunca funciona escrito en el celular (salvo usando adb reverse, ver 5.4).
IP de red (192.168.x.x) → la que conecta un celular físico por Wi-Fi; hay que volver a verificarla cada vez que la laptop cambia de red.
