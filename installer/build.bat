set SAFEBOX_VER=%1%
set WORKSPACE=%2%
set BUILD_NUMBER=%3%
set SVN_REVISION=%4%

if not defined SVN_REVISION set SVN_REVISION=_unkown_version

set IA_PATH=C:\Program Files (x86)\InstallAnywhere 2012

set IA_BTV_SAFEBOX_VER=%SAFEBOX_VER%
set IA_BTV_BUILD_NUMBER=%BUILD_NUMBER%
set IA_BTV_SVN_REVISION=%SVN_REVISION%(branches/alpha_1_2)
set IA_BTV_SRV_IP=192.168.124.158
set IA_BTV_BUILD_SERVER_PATH=%cd%


"%IA_PATH%\build.exe" win\SafeboxSetupWindows.iap_xml
if %errorlevel% neq 0 exit /b %errorlevel%
"%IA_PATH%\build.exe" linux\SafeboxSetupLinux.iap_xml
if %errorlevel% neq 0 exit /b %errorlevel%

copy win\SafeboxSetupWindows_Build_Output\Default_Configuration\Web_Installers\InstData\Windows\VM\Safebox.exe ..\release\SafeboxSetupWindows_%SVN_REVISION%_%BUILD_NUMBER%.exe
if %errorlevel% neq 0 exit /b %errorlevel%
copy linux\SafeboxSetupLinux_Build_Output\Default_Configuration\Web_Installers\InstData\Linux\NoVM\Safebox.bin ..\release\SafeboxSetupLinux_%SVN_REVISION%_%BUILD_NUMBER%.bin
if %errorlevel% neq 0 exit /b %errorlevel%

cd %WORKSPACE%/signfile
	call signfile.bat ..\release\SafeboxSetupWindows_%SVN_REVISION%_%BUILD_NUMBER%.exe
cd %WORKSPACE%/installer


set branchPath=branches\alpha_1_2
set releaseFD=x:\%branchPath%\Safebox_%date:~0,4%%date:~5,2%%date:~8,2%
set downloadShutcut_win=x:\%branchPath%\Safebox_setup_win.exe
set downloadShutcut_linux=x:\%branchPath%\Safebox_setup_linux.bin

mkdir "%releaseFd%"

echo releaseFD=%releaseFd%

copy ..\release\SafeboxSetupWindows_%SVN_REVISION%_%BUILD_NUMBER%.exe "%releaseFd%"
if %errorlevel% neq 0 exit /b %errorlevel%
copy ..\release\SafeboxSetupWindows_%SVN_REVISION%_%BUILD_NUMBER%.exe "%downloadShutcut_win%"
if %errorlevel% neq 0 exit /b %errorlevel%

copy ..\release\SafeboxSetupLinux_%SVN_REVISION%_%BUILD_NUMBER%.bin "%releaseFd%"
if %errorlevel% neq 0 exit /b %errorlevel%
copy ..\release\SafeboxSetupLinux_%SVN_REVISION%_%BUILD_NUMBER%.bin "%downloadShutcut_linux%"
if %errorlevel% neq 0 exit /b %errorlevel%

echo safeboxVersion=%SAFEBOX_VER% > "%releaseFd%/SafeboxSetupWindows_%SVN_REVISION%_%BUILD_NUMBER%_version.txt"
echo svnVersion=%SVN_REVISION% >> "%releaseFd%/SafeboxSetupWindows_%SVN_REVISION%_%BUILD_NUMBER%_version.txt"
echo buildNumber=%BUILD_NUMBER% >> "%releaseFd%/SafeboxSetupWindows_%SVN_REVISION%_%BUILD_NUMBER%_version.txt"
echo releaseTime=%date:~0,4%-%date:~5,2%-%date:~8,2% %time:~0,8% >> "%releaseFd%/SafeboxSetupWindows_%SVN_REVISION%_%BUILD_NUMBER%_version.txt"
copy %releaseFd%\SafeboxSetupWindows_%SVN_REVISION%_%BUILD_NUMBER%_version.txt  x:\%branchPath%\version.txt
if %errorlevel% neq 0 exit /b %errorlevel%

rem for tester's convenience batch
set execBat=%WORKSPACE%\release\SafeboxSetupWindows_%SVN_REVISION%_%BUILD_NUMBER%.bat
set execSh=%WORKSPACE%\release\SafeboxSetupLinux_%SVN_REVISION%_%BUILD_NUMBER%.sh  

echo set /p input_host="Please input the ip address of CSS server:" > %execBat%
echo SafeboxSetupWindows_%SVN_REVISION%_%BUILD_NUMBER%.exe -Dinstall_time_server_ip=%%input_host%% >> %execBat%

move /Y %execBat% "%releaseFd%"
if %errorlevel% neq 0 exit /b %errorlevel%
rem 