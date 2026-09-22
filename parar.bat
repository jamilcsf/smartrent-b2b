@echo off
title SmartRent B2B - Parar
cd /d "%~dp0"

echo Encerrando o SmartRent B2B...
echo.

REM Fecha a janela do servidor, se estiver aberta.
taskkill /fi "WINDOWTITLE eq SmartRent B2B - Servidor*" /t /f >nul 2>&1

REM Encerra o processo Java da aplicacao.
for /f "tokens=2 delims=," %%p in ('tasklist /fi "imagename eq java.exe" /fo csv /nh 2^>nul') do (
    taskkill /pid %%~p /f >nul 2>&1
)
echo   Aplicacao encerrada.

docker rm -f smartrent-pg >nul 2>&1
echo   Banco de dados removido.

echo.
echo Tudo encerrado. Os dados do banco foram descartados;
echo o proximo executar.bat recria tudo do zero.
echo.
pause
