@echo off
setlocal enabledelayedexpansion
title SmartRent B2B - Executar
cd /d "%~dp0"

echo =========================================================
echo   SmartRent B2B
echo =========================================================
echo.

REM ---------------------------------------------------------------
REM 1. Pre-requisitos
REM ---------------------------------------------------------------
echo [1/5] Verificando pre-requisitos...

REM Java: o projeto compila para 17, entao 17 ou superior serve.
set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
    where java >nul 2>&1 && set "JAVA_EXE=java"
)
if not defined JAVA_EXE (
    echo   [ERRO] Java nao encontrado.
    echo          Instale o JDK 17 ou superior e defina JAVA_HOME.
    echo          https://adoptium.net/
    goto :falha
)

REM Le a versao maior do Java. Grava num arquivo antes de parsear: aninhar
REM aspas dentro de for /f quebra quando o caminho do java tem espacos.
"%JAVA_EXE%" -version > "%TEMP%\smartrent_java.txt" 2>&1
set "VER="
for /f "tokens=3" %%v in ('findstr /i "version" "%TEMP%\smartrent_java.txt"') do (
    if not defined VER set "VER=%%~v"
)
del "%TEMP%\smartrent_java.txt" >nul 2>&1
if not defined VER (
    echo   [ERRO] Nao foi possivel identificar a versao do Java.
    goto :falha
)
for /f "delims=. tokens=1" %%a in ("!VER!") do set "VERMAIOR=%%a"
if "!VERMAIOR!"=="1" (
    echo   [ERRO] Java !VER! e antigo demais. O projeto exige 17 ou superior.
    goto :falha
)
if !VERMAIOR! LSS 17 (
    echo   [ERRO] Java !VER! encontrado. O projeto exige 17 ou superior.
    goto :falha
)
echo   Java !VER! ............ ok

REM Docker: usado para subir o PostgreSQL.
docker version >nul 2>&1
if errorlevel 1 (
    echo   [ERRO] Docker nao esta respondendo.
    echo          Abra o Docker Desktop, espere ele terminar de iniciar
    echo          e execute este script de novo.
    goto :falha
)
echo   Docker ................ ok
echo.

REM ---------------------------------------------------------------
REM 2. Banco de dados
REM ---------------------------------------------------------------
REM A porta 55432 evita conflito com um PostgreSQL ja instalado na 5432.
set "PGPORTA=55432"
set "PGNOME=smartrent-pg"
set "SEMEAR=nao"

echo [2/5] Preparando o banco de dados...
docker ps -q -f "name=%PGNOME%" | findstr . >nul
if not errorlevel 1 (
    echo   Container '%PGNOME%' ja esta rodando; reaproveitando os dados.
) else (
    docker rm -f %PGNOME% >nul 2>&1
    docker run -d --name %PGNOME% -p %PGPORTA%:5432 -e POSTGRES_PASSWORD=postgres postgres:16 >nul 2>&1
    if errorlevel 1 (
        echo   [ERRO] Nao foi possivel iniciar o PostgreSQL.
        echo          A porta %PGPORTA% pode estar ocupada.
        goto :falha
    )
    set "SEMEAR=sim"
    echo   Container criado na porta %PGPORTA%.
)

echo   Aguardando o banco aceitar conexao...
set /a TENTATIVA=0
:esperabanco
set /a TENTATIVA+=1
docker exec %PGNOME% pg_isready -U postgres >nul 2>&1
if not errorlevel 1 goto :bancopronto
if !TENTATIVA! GEQ 60 (
    echo   [ERRO] O banco nao respondeu em 60 segundos.
    goto :falha
)
timeout /t 1 /nobreak >nul
goto :esperabanco
:bancopronto
echo   Banco pronto.
echo.

REM ---------------------------------------------------------------
REM 3. Variaveis de ambiente
REM ---------------------------------------------------------------
REM Nenhuma credencial fica no codigo-fonte (RNF03).
set "DATABASE_URL=jdbc:postgresql://localhost:%PGPORTA%/postgres"
set "DATABASE_USERNAME=postgres"
set "DATABASE_PASSWORD=postgres"
if not defined JWT_SECRET set "JWT_SECRET=chave-de-desenvolvimento-local-trocar-em-producao-32b"

echo [3/5] Compilando e iniciando a aplicacao...
echo   (a primeira execucao baixa as dependencias e pode demorar)
echo.

REM Sobe a aplicacao numa janela propria, para este script seguir adiante.
REM Caminho absoluto: em alguns ambientes o cmd nao procura no diretorio
REM atual (NoDefaultCurrentDirectoryInExePath), e "mvnw.cmd" falharia.
start "SmartRent B2B - Servidor" cmd /c ""%~dp0mvnw.cmd" spring-boot:run || pause"

REM ---------------------------------------------------------------
REM 4. Espera a aplicacao responder
REM ---------------------------------------------------------------
echo [4/5] Aguardando a aplicacao subir...
set /a TENTATIVA=0
:esperaapp
set /a TENTATIVA+=1
curl -s -o nul -w "" http://localhost:8080/api/imoveis >nul 2>&1
if not errorlevel 1 goto :apppronta
if !TENTATIVA! GEQ 180 (
    echo   [ERRO] A aplicacao nao respondeu em 3 minutos.
    echo          Veja a janela "SmartRent B2B - Servidor" para o motivo.
    goto :falha
)
timeout /t 1 /nobreak >nul
goto :esperaapp
:apppronta
echo   Aplicacao no ar.
echo.

REM ---------------------------------------------------------------
REM 5. Dados de demonstracao
REM ---------------------------------------------------------------
echo [5/5] Dados de demonstracao...
if "!SEMEAR!"=="sim" (
    docker exec -i %PGNOME% psql -U postgres -q < scripts\dados-demonstracao.sql >nul 2>&1
    if errorlevel 1 (
        echo   [AVISO] Falha ao inserir os dados. O site abre vazio.
    ) else (
        echo   8 imoveis e 5 reservas inseridos.
    )
) else (
    echo   Banco ja existia; dados preservados.
    echo   Para recomecar do zero, rode parar.bat antes deste script.
)

echo.
echo =========================================================
echo   Pronto. Abrindo http://localhost:8080
echo.
echo   Login de demonstracao:
echo     ana@smartrent.dev  /  senhaSegura123
echo.
echo   Para encerrar tudo, rode: parar.bat
echo =========================================================
start "" http://localhost:8080/index.html
echo.
pause
exit /b 0

:falha
echo.
echo Execucao interrompida.
pause
exit /b 1
