
set VC_DIR=E:\Microsoft Visual Studio 10.0\VC
set SVN_DIR=E:\jenkins\workspace\loader\win
set TARGET_DIR=E:\jenkins\workspace\SafeBoxAlpha_1_2\release\win32
set LOG=build.log
set PATH_BAK=%PATH%

call "%VC_DIR%\vcvarsall.bat" x86

set PRJ=Safebox
msbuild "%SVN_DIR%\%PRJ%\%PRJ%.sln" /t:%PRJ%:Rebuild /p:Configuration=Release /p:platform=win32 > "%LOG%"
copy "%SVN_DIR%\%PRJ%\Release\%PRJ%.exe" "%TARGET_DIR%"
echo ========================================================================= >> "%LOG%"

set PRJ=S3Ext
msbuild "%SVN_DIR%\%PRJ%\%PRJ%.sln" /t:%PRJ%:Rebuild /p:Configuration=Release /p:platform=win32 >> "%LOG%"
copy "%SVN_DIR%\%PRJ%\Release\%PRJ%.dll" "%TARGET_DIR%"
echo ========================================================================= >> "%LOG%"

set PRJ=S3Icon
msbuild "%SVN_DIR%\%PRJ%\%PRJ%.sln" /t:%PRJ%:Rebuild /p:Configuration=Release /p:platform=win32 >> "%LOG%"
copy "%SVN_DIR%\%PRJ%\Release\%PRJ%.dll" "%TARGET_DIR%"
echo ========================================================================= >> "%LOG%"

set PRJ=S3Icon1
msbuild "%SVN_DIR%\%PRJ%\%PRJ%.sln" /t:%PRJ%:Rebuild /p:Configuration=Release /p:platform=win32 >> "%LOG%"
copy "%SVN_DIR%\%PRJ%\Release\%PRJ%.dll" "%TARGET_DIR%"

set PATH=%PATH_BAK%
echo.
echo.
findstr /r /c:"[1-9][0-9]* Error(s)" %LOG%
echo.
if not errorlevel 1 (
    echo Please check "%LOG%" for build errors
) else (
	echo Successful build
)
