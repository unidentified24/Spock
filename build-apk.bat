@echo off
setlocal
cd /d "%~dp0"
if exist "build-env.bat" call "build-env.bat"
if not exist "keystore.properties" (
 echo Configure release signing first. See RELEASING.md.
 exit /b 1
)
call gradlew.bat :app:assembleRelease --no-daemon %*
if errorlevel 1 exit /b %ERRORLEVEL%
if not exist "Apk" mkdir "Apk"
copy /Y "app\build\outputs\apk\release\app-release.apk" "Apk\Spock.apk" >nul
exit /b %ERRORLEVEL%
