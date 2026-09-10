@echo off
setlocal
cd /d "%~dp0"
if not exist ".venv\Scripts\python.exe" (
    py -3 -m venv .venv
    if errorlevel 1 goto error
)
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 goto error
".venv\Scripts\python.exe" server.py --seed-demo
pause
exit /b
:error
echo No se pudo iniciar. Instale Python 3.10 o superior desde python.org y vuelva a ejecutar.
pause
