@echo off
set /p VERSION=Neue Versionsnummer (z.B. 1.3.0): 
set /p TEXT=Was ist neu (kurz, darf leer sein): 
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0veroeffentlichen.ps1" -Version "%VERSION%" -Text "%TEXT%"
echo.
pause
