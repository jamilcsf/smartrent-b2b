@echo off
setlocal enabledelayedexpansion

title Script de Implantacao - SmartRent B2B
cls

echo =========================================================
echo       SMARTRENT B2B - SCRIPT DE IMPLANTACAO (ISI)
echo =========================================================
echo.

set /p DIR_BASE="Informe o diretorio base de implantacao (ex: C:\Sistemas): "

if "%DIR_BASE%"=="" (
    echo [ERRO] Diretorio nao pode ser em branco. Operacao cancelada.
    pause
    exit /b 1
)

set ESTRUTURA_DIR=%DIR_BASE%\SmartRentB2B
set PASTA_BIN=%ESTRUTURA_DIR%\bin
set PASTA_CONFIG=%ESTRUTURA_DIR%\config
set PASTA_LOGS=%ESTRUTURA_DIR%\logs
set ARQ_LOG=%PASTA_LOGS%\instalacao.log

echo [%DATE% %TIME%] [INFO] Criando estrutura de diretorios...
mkdir "%PASTA_BIN%" 2>nul
mkdir "%PASTA_CONFIG%" 2>nul
mkdir "%PASTA_LOGS%" 2>nul

echo [%DATE% %TIME%] [INFO] Inicio do processo de implantacao > "%ARQ_LOG%"
echo [%DATE% %TIME%] [OK] Diretorios criados em %ESTRUTURA_DIR% >> "%ARQ_LOG%"

set ARQ_JAR_ORIGEM=origem\SmartRentB2B.jar
set ARQ_INI_ORIGEM=origem\application.ini

if not exist "%ARQ_JAR_ORIGEM%" (
    echo [%DATE% %TIME%] [ERRO] Arquivo de execucao %ARQ_JAR_ORIGEM% nao foi encontrado! >> "%ARQ_LOG%"
    echo [ERRO] Executavel de origem nao encontrado. Crie a pasta 'origem' com o arquivo SmartRentB2B.jar.
    pause
    exit /b 1
)

copy /Y "%ARQ_JAR_ORIGEM%" "%PASTA_BIN%\" >nul
if %ERRORLEVEL% EQU 0 (
    echo [%DATE% %TIME%] [OK] Executavel implantado em %PASTA_BIN% >> "%ARQ_LOG%"
) else (
    echo [%DATE% %TIME%] [ERRO] Falha ao copiar executavel para %PASTA_BIN% >> "%ARQ_LOG%"
    pause
    exit /b 1
)

if not exist "%ARQ_INI_ORIGEM%" (
    echo [%DATE% %TIME%] [AVISO] Arquivo application.ini nao encontrado. Criando padrao... >> "%ARQ_LOG%"
    echo server.port=8080 > "%PASTA_CONFIG%\application.ini"
) else (
    copy /Y "%ARQ_INI_ORIGEM%" "%PASTA_CONFIG%\" >nul
    echo [%DATE% %TIME%] [OK] Arquivo de configuracao implantado em %PASTA_CONFIG% >> "%ARQ_LOG%"
)

echo [%DATE% %TIME%] [OK] Implantacao concluida com sucesso. >> "%ARQ_LOG%"
echo.
echo =========================================================
echo       IMPLANTACAO CONCLUIDA COM SUCESSO!
echo  Logs gravados em: %ARQ_LOG%
echo =========================================================
echo.
pause