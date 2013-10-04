set VER=v1.2
setx SAFEBOX_VER %VER%

echo %VER% > release\win32\ver.txt
echo %VER% > release\linux\ver.txt

apache-maven-3.0.4\bin\mvn.bat clean package -Dmaven.test.skip=true -Dmaven.compiler.debug=true


