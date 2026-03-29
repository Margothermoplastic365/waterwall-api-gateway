@echo off
set JAVA_HOME=C:\java\jdk-21.0.1
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d F:\spring_cloud\spring_cloud\api-gateway

echo ============================================
echo   API Gateway Platform - Starting All Services
echo ============================================

echo.
echo [1/7] Starting Identity Service (port 8081)...
start "Identity Service" cmd /c "java -jar identity-service\target\identity-service-1.0.0-SNAPSHOT.jar"
echo      Waiting 15 seconds for identity to initialize...
timeout /t 15 /nobreak >nul

echo [2/7] Starting Management API (port 8082)...
start "Management API" cmd /c "java -jar management-api\target\management-api-1.0.0-SNAPSHOT.jar"
timeout /t 5 /nobreak >nul

echo [3/7] Starting Gateway Runtime (port 8080 + 9090)...
start "Gateway Runtime" cmd /c "java -jar gateway-runtime\target\gateway-runtime-1.0.0-SNAPSHOT.jar"
timeout /t 5 /nobreak >nul

echo [4/7] Starting Analytics Service (port 8083)...
start "Analytics Service" cmd /c "java -jar analytics-service\target\analytics-service-1.0.0-SNAPSHOT.jar"
timeout /t 3 /nobreak >nul

echo [5/7] Starting Notification Service (port 8084)...
start "Notification Service" cmd /c "java -jar notification-service\target\notification-service-1.0.0-SNAPSHOT.jar"
timeout /t 3 /nobreak >nul

echo [6/7] Starting Developer Portal (port 3000)...
start "Portal" cmd /c "cd gateway-portal && npx next start -p 3000"
timeout /t 3 /nobreak >nul

echo [7/7] Starting Admin Dashboard (port 3001)...
start "Admin" cmd /c "cd gateway-admin && npx next start -p 3001"

echo.
echo ============================================
echo   All services starting!
echo ============================================
echo.
echo   Portal:  http://localhost:3000
echo   Admin:   http://localhost:3001
echo   Gateway: http://localhost:8080
echo   Login:   admin@gateway.local / changeme
echo.
echo   RabbitMQ: amqp://guest:guest@localhost:5672/cem
echo   Database: postgres://postgres:postgres@localhost:5432/gateway
echo.
echo   Wait ~30 seconds for all services to be ready.
echo ============================================
pause
