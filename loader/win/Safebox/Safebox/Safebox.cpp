// Safebox.cpp : Defines the entry point for the application.
//

#include "stdafx.h"
#include "Safebox.h"
#include <windows.h>
#include <string.h>
#include <wchar.h>
#include <stdio.h>
#include <shellapi.h>
#include <atlbase.h>

#include "winnls.h"
#include "shobjidl.h"
#include "objbase.h"
#include "objidl.h"
#include "shlguid.h"
#include "strsafe.h"

INT_PTR CALLBACK About(HWND hDlg, UINT message, WPARAM wParam, LPARAM lParam);
TCHAR gszID[64]= _T("");
BOOL bCancel= FALSE;

bool IsWin7()  
{  
	OSVERSIONINFOEX osvi;  
	BOOL bOsVersionInfoEx;  
      
	ZeroMemory(&osvi, sizeof(OSVERSIONINFOEX));  
	osvi.dwOSVersionInfoSize = sizeof(OSVERSIONINFOEX);  
	bOsVersionInfoEx = GetVersionEx((OSVERSIONINFO*) &osvi);  
      
	// win7 ver: NT6.1   
	if ( VER_PLATFORM_WIN32_NT == osvi.dwPlatformId &&    
		osvi.dwMajorVersion == 6 &&   
		osvi.dwMinorVersion == 1 )  
	{  
		return true;      
	}  
	else  
	{
		return false;  
	}
}  

void RunProcess(TCHAR* szCmdline)
{
	STARTUPINFO si;
    PROCESS_INFORMATION pi;

	ZeroMemory( &si, sizeof(si) );
	ZeroMemory( &pi, sizeof(pi) ); 
	si.cb = sizeof(si);
	si.wShowWindow= SW_HIDE;

    // Start the child process. 
	if( !CreateProcess( NULL,   // No module name (use command line)
		szCmdline,      // Command line
		NULL,           // Process handle not inheritable
		NULL,           // Thread handle not inheritable
		FALSE,          // Set handle inheritance to FALSE
		CREATE_UNICODE_ENVIRONMENT,              // No creation flags
		NULL/*szEnvir*/,           // Use parent's environment block
		NULL,  // Starting directory 
		&si,            // Pointer to STARTUPINFO structure
		&pi )           // Pointer to PROCESS_INFORMATION structure
       ) 
	{
		printf("CreateProcess failed (%d)./n", GetLastError() );		
	}
	else
	{
		// Close process and thread handles. 
		CloseHandle( pi.hProcess );
		CloseHandle( pi.hThread );
	}
}


// CreateLink - Uses the Shell's IShellLink and IPersistFile interfaces 
//              to create and store a shortcut to the specified object. 
//
HRESULT CreateLink(BOOL bFolder, LPCWSTR lpszPathObj, LPCWSTR lpszPathLink, LPCWSTR lpszDesc, LPCWSTR lpszIconPath) 
{ 
    HRESULT hres; 
    IShellLink* psl; 

	CoInitialize(NULL);

    // Get a pointer to the IShellLink interface. It is assumed that CoInitialize
    // has already been called.	
    hres = CoCreateInstance(bFolder?CLSID_FolderShortcut:CLSID_ShellLink, NULL, CLSCTX_INPROC_SERVER, IID_IShellLink, (LPVOID*)&psl); 
    if (SUCCEEDED(hres)) 
    { 
        IPersistFile* ppf; 
 
        // Set the path to the shortcut target and add the description. 
        psl->SetPath(lpszPathObj); 
        psl->SetDescription(lpszDesc); 
		psl->SetIconLocation(lpszIconPath, 0);
 
        // Query IShellLink for the IPersistFile interface, used for saving the 
        // shortcut in persistent storage. 
        hres = psl->QueryInterface(IID_IPersistFile, (LPVOID*)&ppf); 
 
        if (SUCCEEDED(hres)) 
        { 
            WCHAR wsz[MAX_PATH]; 
 
            // Ensure that the string is Unicode. 
            //MultiByteToWideChar(CP_ACP, 0, lpszPathLink, -1, wsz, MAX_PATH); 
			wcscpy(wsz, lpszPathLink);
            
            // Add code here to check return value from MultiByteWideChar 
            // for success.
 
            // Save the link by calling IPersistFile::Save. 
            hres = ppf->Save(wsz, TRUE); 
            ppf->Release(); 
        } 
        psl->Release(); 
    } 

	CoUninitialize();
    return hres; 
}

void RemoveDir(LPCTSTR dir) // Fully qualified name of the directory being deleted, without trailing backslash 
{     
	SHFILEOPSTRUCT file_op = {         
		NULL,         
		FO_DELETE,         
		dir,         
		L"",         
		FOF_NOCONFIRMATION | FOF_NOERRORUI |  FOF_SILENT,         
		false,         
		0,         
		L"" };    
		SHFileOperation(&file_op); 
}

BOOL FileExist( LPCTSTR filename )
{
	WIN32_FIND_DATA findData;

	ZeroMemory(&findData, sizeof(findData));

	// Search the file
	HANDLE hFind = FindFirstFile( filename, &findData );
	if ( hFind == INVALID_HANDLE_VALUE )
	{	
		return FALSE;
	}

	FindClose( hFind );
	return TRUE;
}

BOOL DetectJava() {
	BOOL bDetected= FALSE;
	int i,iLen;
	char *pPath,*pFind,*pBeg,*pEnd,*pTail;
	TCHAR wszPath[MAX_PATH];

	pPath= getenv("PATH");
	pFind= strstr(pPath,"\\Java\\");
	iLen= strlen(pPath);
	if( pFind!=NULL )
	{
		pTail= &pPath[iLen];
		pBeg= pFind;
		pEnd= pFind;
		while( *pBeg!=';' && pBeg>pPath ) 
			pBeg--;
		while( *pEnd!=';' && pEnd<pTail ) 
			pEnd++;

		if( (pBeg[0]==';' || pBeg==pPath) && (pEnd[0]==';' || pEnd==pTail) )
		{
			pBeg++;
			pEnd[0]= '\0';			
			strcat(pBeg, "\\java.exe");
			iLen= strlen(pBeg);

			for(i=0; i<iLen; i++) 
				wszPath[i]= pBeg[i];
			wszPath[i]= '\0';

			if( FileExist(wszPath) )
				bDetected= TRUE;
//			MessageBoxA(0, pBeg, "Safebox", MB_OK);
		}
	}
	else 
	{
		MessageBox(NULL, _T("Please have JVM on the platfrom"), _T("Safebox"), MB_ICONINFORMATION);
	}

	return bDetected;
}

BOOL RunJava(TCHAR* szStartPath, TCHAR* lpCmdLine)
{
	STARTUPINFO si;
    PROCESS_INFORMATION pi;
    TCHAR szCmdline[MAX_PATH];
	TCHAR szJavaPath[32];
	bool bRequireConsole= false;

	if( lstrlen(lpCmdLine)>0 )
	{
		if( wcsncmp(lpCmdLine,L"-exit",5)==0 ||
			wcsncmp(lpCmdLine,L"-web",4)==0 ||
			wcsncmp(lpCmdLine,L"-info",5)==0 )
			bRequireConsole= false;
		else
			bRequireConsole= true;
	}

	lstrcpy(szJavaPath, _T("jre\\bin\\java"));

	if( FileExist(_T("jre\\bin\\java.exe")) ) 
		lstrcpy(szJavaPath, _T("jre\\bin\\java"));
	else
		lstrcpy(szJavaPath, _T("java"));

	if( !bRequireConsole ) 
		lstrcat(szJavaPath, _T("w"));

	lstrcpy(szCmdline,szJavaPath);
	lstrcat(szCmdline, _T(" -Xmx1024m -Dfile.encoding=utf-8 -Dsun.java2d.d3d=false -cp .\\*; Main "));
	lstrcat(szCmdline, lpCmdLine);

	ZeroMemory( &si, sizeof(si) );
	ZeroMemory( &pi, sizeof(pi) ); 
	si.cb = sizeof(si);
	si.wShowWindow= SW_HIDE;

    // Start the child process. 
	if( !CreateProcess( NULL,   // No module name (use command line)
		szCmdline,      // Command line
		NULL,           // Process handle not inheritable
		NULL,           // Thread handle not inheritable
		FALSE,          // Set handle inheritance to FALSE
		CREATE_UNICODE_ENVIRONMENT,              // No creation flags
		NULL/*szEnvir*/,           // Use parent's environment block
		szStartPath,  // Starting directory 
		&si,            // Pointer to STARTUPINFO structure
		&pi )           // Pointer to PROCESS_INFORMATION structure
       ) 
	{
		printf("CreateProcess failed (%d)./n", GetLastError() );		
	}
	else
	{
		// Close process and thread handles. 
		CloseHandle( pi.hProcess );
		CloseHandle( pi.hThread );
	}
	
	return TRUE;
}

BOOL RunJava_Anci(TCHAR* m_lpCmdLine)
{
	char aszCmd[MAX_PATH],aszParam[MAX_PATH];
	int i,iLen= lstrlen(m_lpCmdLine);
	bool bRequireConsole= false;

	for(i=0; i<iLen; i++)
		aszParam[i]= (char)m_lpCmdLine[i];
	aszParam[i]= '\0';

	if( iLen>0 )
	{
		if( strncmp(aszParam,"-exit",5)!=0 &&
			strncmp(aszParam,"-web",4)!=0 &&
			strncmp(aszParam,"-info",5)!=0 )
		bRequireConsole= true;
	}

	if( bRequireConsole ) 
		strcpy(aszCmd, "java -cp .\\*; Main ");
	else
		strcpy(aszCmd, "javaw -cp .\\*; Main ");
	strcat(aszCmd, aszParam);
	
	//Method 1
	//WinExec("del /f c:\\temp\\safebox.log", SW_HIDE);
	WinExec(aszCmd, bRequireConsole?SW_SHOWNORMAL:SW_HIDE);

	//Method 2
	//ShellExecute(NULL, _T("open"), _T("java"), _T("-cp .\\*; Main"), NULL, SW_SHOWNORMAL);

	//Method 3
	/*
	STARTUPINFO si;
    PROCESS_INFORMATION pi;
    LPTSTR szCmdline=_tcsdup(TEXT("java -cp .\\*; Main"));

    ZeroMemory( &si, sizeof(si) );
	ZeroMemory( &pi, sizeof(pi) ); 
    si.cb = sizeof(si);
	si.wShowWindow= (iLen==0?SW_HIDE:SW_SHOWNORMAL);

    // Start the child process. 
    if( !CreateProcess( NULL,   // No module name (use command line)
       szCmdline,      // Command line
       NULL,           // Process handle not inheritable
       NULL,           // Thread handle not inheritable
       FALSE,          // Set handle inheritance to FALSE
       0,              // No creation flags
       NULL,           // Use parent's environment block
       NULL,           // Use parent's starting directory 
       &si,            // Pointer to STARTUPINFO structure
       &pi )           // Pointer to PROCESS_INFORMATION structure
       ) 
    {
       printf( "CreateProcess failed (%d)./n", GetLastError() );
	   MessageBox(0, _T("FAIL"), _T("Safebox"), MB_OK);
       return TRUE;
    }

    // Wait until child process exits.
    WaitForSingleObject( pi.hProcess, INFINITE ); 

    // Close process and thread handles. 
    CloseHandle( pi.hProcess );
    CloseHandle( pi.hThread );
	*/
	return TRUE;
}

CONST TCHAR* S3ExtCLSID= _T("{760669DD-E7AD-4ACD-B921-097B075A7711}");
CONST TCHAR* S3IconCLSID= _T("{1B649A19-7158-436C-841E-F168CB5C1456}");
CONST TCHAR* S3EncIconCLSID= _T("{ABC4EA80-B324-4C84-95A7-E8FC767ED68F}");
CONST TCHAR* SafeboxVer= _T("v1.15");

void RunCmdLine(TCHAR* szCmdline)
{
	STARTUPINFO si;
    PROCESS_INFORMATION pi;

    ZeroMemory( &si, sizeof(si) );
	ZeroMemory( &pi, sizeof(pi) ); 
    si.cb = sizeof(si);
	si.wShowWindow= (SW_HIDE);

    // Start the child process. 
    if( !CreateProcess( NULL,   // No module name (use command line)
       szCmdline,      // Command line
       NULL,           // Process handle not inheritable
       NULL,           // Thread handle not inheritable
       FALSE,          // Set handle inheritance to FALSE
       0,              // No creation flags
       NULL,           // Use parent's environment block
       NULL,           // Use parent's starting directory 
       &si,            // Pointer to STARTUPINFO structure
       &pi )           // Pointer to PROCESS_INFORMATION structure
       ) 
    {
       printf( "CreateProcess failed (%d)./n", GetLastError() );
       return;
    }

    //Wait until child process exits.
    //WaitForSingleObject( pi.hProcess, INFINITE ); 

    // Close process and thread handles. 
    CloseHandle( pi.hProcess );
    CloseHandle( pi.hThread );
}

void RegisterService(TCHAR* szExeDir, TCHAR* szDll, BOOL bRegister)
{
	TCHAR szCmd[MAX_PATH];

	if( bRegister==TRUE )
		lstrcpy(szCmd, _T("regsvr32 /c /s \""));
	else
		lstrcpy(szCmd, _T("regsvr32 /u /s \""));
	lstrcat(szCmd, szExeDir);
	lstrcat(szCmd, szDll);
	lstrcat(szCmd, _T("\""));
	RunCmdLine(szCmd);
}

void Install(TCHAR* szExeDir, TCHAR* szSyncDir)
{
	CRegKey reg;
	LONG    lRet;
	TCHAR szTemp[MAX_PATH];
	TCHAR szExes[MAX_PATH];
	TCHAR szExex[MAX_PATH];
	TCHAR szRuns[MAX_PATH];
	TCHAR szRunx[MAX_PATH];

	//if ( 0 == (GetVersion() & 0x80000000) )
	{    
		//Setup shell extension
		lRet= reg.Open(HKEY_LOCAL_MACHINE, _T("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Shell Extensions\\Approved"), KEY_WRITE );
		if( lRet==ERROR_SUCCESS )
		{
			lRet= reg.SetStringValue(S3ExtCLSID, _T("Safebox shell extension"));
			reg.Close();
		}

		lRet= reg.Open(HKEY_LOCAL_MACHINE, _T("Software"), KEY_WRITE );
		if( lRet==ERROR_SUCCESS )
		{
			reg.SetKeyValue(_T("Safebox"), SafeboxVer, NULL);
			reg.Close();
			lRet= reg.Open(HKEY_LOCAL_MACHINE, _T("Software\\Safebox"), KEY_WRITE );
			if( lRet==ERROR_SUCCESS )
			{
				reg.SetStringValue(_T("InstallPath"), szExeDir );
				reg.SetStringValue(_T("SyncPath"), szSyncDir);
				reg.Close();
			}
		}
		
		lRet = reg.Open(HKEY_LOCAL_MACHINE, _T("SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion"), KEY_WRITE);
		if (lRet == ERROR_SUCCESS) {
			reg.SetKeyValue(_T("AppCompatFlags"), NULL, NULL);
			reg.Close();

			lRet = reg.Open(HKEY_LOCAL_MACHINE, _T("SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags"), KEY_WRITE);
			if (lRet == ERROR_SUCCESS) {
				reg.SetKeyValue(_T("Layers"), NULL, NULL);
				reg.Close();

				lRet = reg.Open(HKEY_LOCAL_MACHINE, _T("SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers"), KEY_WRITE);
				if (lRet == ERROR_SUCCESS) {
//#if (_WIN64)
					lstrcpy(szExes, szExeDir);
					lstrcat(szExes, _T("\\Safebox64.exe"));
					reg.SetStringValue(szExes, _T("RunAsInvoker"));
//#else
					lstrcpy(szExex, szExeDir);
					lstrcat(szExex, _T("\\Safebox.exe"));
					reg.SetStringValue(szExex, _T("RunAsInvoker"));
//#endif
					reg.Close();
				}
			}
		}

		lRet = reg.Open(HKEY_CURRENT_USER, _T("SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion"), KEY_WRITE);
		if (lRet == ERROR_SUCCESS) {
			reg.SetKeyValue(_T("AppCompatFlags"), NULL, NULL);
			reg.Close();

			lRet = reg.Open(HKEY_CURRENT_USER, _T("SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags"), KEY_WRITE);
			if (lRet == ERROR_SUCCESS) {
				reg.SetKeyValue(_T("Layers"), NULL, NULL);
				reg.Close();

				lRet = reg.Open(HKEY_CURRENT_USER, _T("SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers"), KEY_WRITE);
				if (lRet == ERROR_SUCCESS) {
//#if (_WIN64)
					lstrcpy(szExes, szExeDir);
					lstrcat(szExes, _T("\\Safebox64.exe"));
					reg.SetStringValue(szExes, _T("RunAsInvoker"));
//#else
					lstrcpy(szExex, szExeDir);
					lstrcat(szExex, _T("\\Safebox.exe"));
					reg.SetStringValue(szExex, _T("RunAsInvoker"));
//#endif
					reg.Close();
				}
			}
		}
		
		lRet = reg.Open(HKEY_LOCAL_MACHINE, _T("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run"), KEY_WRITE);
		if (lRet == ERROR_SUCCESS) {
//#if (_WIN64)
			lstrcpy(szRuns, _T("\""));
			lstrcat(szExes, _T("\""));
			lstrcat(szRuns, szExes);
			reg.SetStringValue(_T("Safebox64"), szRuns);
//#else
			lstrcpy(szRunx, _T("\""));
			lstrcat(szExex, _T("\""));
			lstrcat(szRunx, szExex);
			reg.SetStringValue(_T("SafeboxXP"), szRunx);
//#endif
			reg.Close();
		}
		/*
		lRet = reg.Open(HKEY_CURRENT_USER, _T("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run"), KEY_WRITE);
		if (lRet == ERROR_SUCCESS) {
//#if (_WIN64)
			lstrcpy(szRuns, _T("\""));
			lstrcat(szExes, _T("\""));
			lstrcat(szRuns, szExes);
			reg.SetStringValue(_T("Safebox64bit"), szRuns);
//#else
			lstrcpy(szRunx, _T("\""));
			lstrcat(szExex, _T("\""));
			lstrcat(szRunx, szExex);
			reg.SetStringValue(_T("Safebox32bit"), szRunx);
//#endif
			reg.Close();
		}
		*/
		lRet= reg.Open(HKEY_CLASSES_ROOT, _T("*\\ShellEx\\ContextMenuHandlers"), KEY_WRITE );
		if( lRet==ERROR_SUCCESS )
		{
			reg.SetKeyValue(_T("S3ShlExt"), S3ExtCLSID, NULL);
			reg.Close();
		}

		lRet= reg.Open(HKEY_CLASSES_ROOT, _T("Directory\\ShellEx\\ContextMenuHandlers"), KEY_WRITE );
		if( lRet==ERROR_SUCCESS )
		{
			reg.SetKeyValue(_T("S3ShlExt"), S3ExtCLSID, NULL);
			reg.Close();
		}

		lRet= reg.Open(HKEY_CLASSES_ROOT, _T("CLSID"), KEY_WRITE);
		if( lRet==ERROR_SUCCESS )
		{
			reg.SetKeyValue(S3ExtCLSID, _T("Safebox Ext"), NULL);
			reg.Close();

			lstrcpy(szTemp, _T("CLSID\\"));
			lstrcat(szTemp, S3ExtCLSID);
			lRet= reg.Open(HKEY_CLASSES_ROOT, szTemp, KEY_WRITE);
			if( lRet==ERROR_SUCCESS )
			{
				lstrcpy(szTemp, szExeDir);
#if (_WIN64)
				lstrcat(szTemp, _T("\\s3Ext64.dll"));
#else
				lstrcat(szTemp, _T("\\s3Ext.dll"));
#endif
				reg.SetKeyValue(_T("InprocServer32"), szTemp, NULL);
				reg.SetKeyValue(_T("InprocServer32\\ThreadingModel"), _T("Apartment"), NULL);
				reg.Close();
			}
		}

		//Setup shell overlay icon
		lRet= reg.Open(HKEY_LOCAL_MACHINE, 
			_T("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\ShellIconOverlayIdentifiers"), KEY_WRITE );
		if( lRet==ERROR_SUCCESS )
		{
			//OverlayIdentifiers limited to 15 so set '0' as prefix
			reg.SetKeyValue(_T("0 SafeboxIcon"), S3IconCLSID, NULL); 
			reg.Close();
		}

		lRet= reg.Open(HKEY_LOCAL_MACHINE, 
			_T("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\ShellIconOverlayIdentifiers"), KEY_WRITE );
		if( lRet==ERROR_SUCCESS )
		{
			reg.SetKeyValue(_T("0 SafeboxIcon1"), S3EncIconCLSID, NULL);
			reg.Close();
		}

		lRet= reg.Open(HKEY_CLASSES_ROOT, _T("CLSID"), KEY_WRITE);
		if( lRet==ERROR_SUCCESS )
		{
			reg.SetKeyValue(S3IconCLSID, _T("Safebox Icon"), NULL);
			reg.Close();

			lstrcpy(szTemp, _T("CLSID\\"));
			lstrcat(szTemp, S3IconCLSID);
			lRet= reg.Open(HKEY_CLASSES_ROOT, szTemp, KEY_WRITE);
			if( lRet==ERROR_SUCCESS )
			{
				lstrcpy(szTemp, szExeDir);
#if (_WIN64)
				lstrcat(szTemp, _T("\\s3Icon64.dll"));
#else
				lstrcat(szTemp, _T("\\s3Icon.dll"));
#endif
				reg.SetKeyValue(_T("InprocServer32"), szTemp, NULL);
				reg.SetKeyValue(_T("InprocServer32\\ThreadingModel"), _T("Apartment"), NULL);
				reg.Close();
			}
		}

		lRet= reg.Open(HKEY_CLASSES_ROOT, _T("CLSID"), KEY_WRITE);
		if( lRet==ERROR_SUCCESS )
		{
			reg.SetKeyValue(S3EncIconCLSID, _T("Safebox Enc Icon"), NULL);
			reg.Close();

			lstrcpy(szTemp, _T("CLSID\\"));
			lstrcat(szTemp, S3EncIconCLSID);
			lRet= reg.Open(HKEY_CLASSES_ROOT, szTemp, KEY_WRITE);
			if( lRet==ERROR_SUCCESS )
			{
				lstrcpy(szTemp, szExeDir);
#if (_WIN64)
				lstrcat(szTemp, _T("\\s3Icon164.dll"));
#else
				lstrcat(szTemp, _T("\\s3Icon1.dll"));
#endif
				reg.SetKeyValue(_T("InprocServer32"), szTemp, NULL);
				reg.SetKeyValue(_T("InprocServer32\\ThreadingModel"), _T("Apartment"), NULL);
				reg.Close();
			}
		}

		//Setup Safebox app
		lRet= reg.Open(HKEY_LOCAL_MACHINE, _T("Software"), KEY_WRITE );
		if( lRet==ERROR_SUCCESS )
		{
			reg.SetKeyValue(_T("Safebox"), SafeboxVer, NULL);
			reg.Close();
			lRet= reg.Open(HKEY_LOCAL_MACHINE, _T("Software\\Safebox"), KEY_WRITE );
			if( lRet==ERROR_SUCCESS )
			{
				reg.SetStringValue(_T("InstallPath"), szExeDir );
				reg.SetStringValue(_T("SyncPath"), szSyncDir);
				reg.Close();
			}
		}		
	}

#if (_WIN64)
		lRet= reg.Open(HKEY_CLASSES_ROOT, _T("Wow6432Node\\CLSID"), KEY_WRITE);
		if( lRet==ERROR_SUCCESS )
		{
			reg.SetKeyValue(S3ExtCLSID, _T("Safebox Ext"), NULL);
			reg.Close();
			lstrcpy(szTemp, _T("Wow6432Node\\CLSID\\"));
			lstrcat(szTemp, S3ExtCLSID);
			lRet= reg.Open(HKEY_CLASSES_ROOT, szTemp, KEY_WRITE);
			if( lRet==ERROR_SUCCESS )
			{
				lstrcpy(szTemp, szExeDir);
				lstrcat(szTemp, _T("\\s3Ext.dll"));
				reg.SetKeyValue(_T("InprocServer32"), szTemp, NULL);
				reg.SetKeyValue(_T("InprocServer32\\ThreadingModel"), _T("Apartment"), NULL);
				reg.Close();
			}
		}

		lRet= reg.Open(HKEY_CLASSES_ROOT, _T("Wow6432Node\\CLSID"), KEY_WRITE);
		if( lRet==ERROR_SUCCESS )
		{
			reg.SetKeyValue(S3IconCLSID, _T("Safebox Icon"), NULL);
			reg.Close();

			lstrcpy(szTemp, _T("Wow6432Node\\CLSID\\"));
			lstrcat(szTemp, S3IconCLSID);
			lRet= reg.Open(HKEY_CLASSES_ROOT, szTemp, KEY_WRITE);
			if( lRet==ERROR_SUCCESS )
			{
				lstrcpy(szTemp, szExeDir);
				lstrcat(szTemp, _T("\\s3Icon.dll"));
				reg.SetKeyValue(_T("InprocServer32"), szTemp, NULL);
				reg.SetKeyValue(_T("InprocServer32\\ThreadingModel"), _T("Apartment"), NULL);
				reg.Close();
			}
		}

		lRet= reg.Open(HKEY_CLASSES_ROOT, _T("Wow6432Node\\CLSID"), KEY_WRITE);
		if( lRet==ERROR_SUCCESS )
		{
			reg.SetKeyValue(S3EncIconCLSID, _T("Safebox Enc Icon"), NULL);
			reg.Close();

			lstrcpy(szTemp, _T("Wow6432Node\\CLSID\\"));
			lstrcat(szTemp, S3EncIconCLSID);
			lRet= reg.Open(HKEY_CLASSES_ROOT, szTemp, KEY_WRITE);
			if( lRet==ERROR_SUCCESS )
			{
				lstrcpy(szTemp, szExeDir);
				lstrcat(szTemp, _T("\\s3Icon1.dll"));
				reg.SetKeyValue(_T("InprocServer32"), szTemp, NULL);
				reg.SetKeyValue(_T("InprocServer32\\ThreadingModel"), _T("Apartment"), NULL);
				reg.Close();
			}
		}
#endif

	//Register shell extension & overlay icon dlls
#if (_WIN64)
	RegisterService(szExeDir, _T("\\s3Ext64.dll"), TRUE);
	RegisterService(szExeDir, _T("\\s3Icon64.dll"), TRUE);
	RegisterService(szExeDir, _T("\\s3Icon164.dll"), TRUE);
#else
	RegisterService(szExeDir, _T("\\s3Ext.dll"), TRUE);
	RegisterService(szExeDir, _T("\\s3Icon.dll"), TRUE);
	RegisterService(szExeDir, _T("\\s3Icon1.dll"), TRUE);
#endif
}

void Uninstall(TCHAR* szExeDir)
{
	CRegKey reg;
	TCHAR szExes[MAX_PATH];
	TCHAR szExex[MAX_PATH];
	TCHAR szRuns[MAX_PATH];
	TCHAR szRunx[MAX_PATH];

	//Unregister shell extension & overlay icon dlls
#if (_WIN64)
	RegisterService(szExeDir, _T("\\s3Ext64.dll"), FALSE);
	RegisterService(szExeDir, _T("\\s3Icon64.dll"), FALSE);
	RegisterService(szExeDir, _T("\\s3Icon164.dll"), FALSE);
#else
	RegisterService(szExeDir, _T("\\s3Ext.dll"), FALSE);
	RegisterService(szExeDir, _T("\\s3Icon.dll"), FALSE);
	RegisterService(szExeDir, _T("\\s3Icon1.dll"), FALSE);
#endif

	//if ( 0 == (GetVersion() & 0x80000000) )
	{
		//Remove shell extension setting
		if ( ERROR_SUCCESS == 
			reg.Open ( HKEY_LOCAL_MACHINE, _T("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Shell Extensions\\Approved"), KEY_WRITE ) )
		{
			reg.DeleteValue ( S3ExtCLSID );
			reg.Close();
		}

		if (ERROR_SUCCESS == reg.Open(HKEY_LOCAL_MACHINE, _T("SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers"), KEY_WRITE)) {
//#if (_WIN64)
			lstrcpy(szExes, szExeDir);
			lstrcat(szExes, _T("\\Safebox64.exe"));
			reg.DeleteValue(szExes);
//#else
			lstrcpy(szExex, szExeDir);
			lstrcat(szExex, _T("\\Safebox.exe"));
			reg.DeleteValue(szExex);
//#endif
			reg.Close();
		}

		if (ERROR_SUCCESS == reg.Open(HKEY_CURRENT_USER, _T("SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers"), KEY_WRITE)) {
//#if (_WIN64)
			lstrcpy(szExes, szExeDir);
			lstrcat(szExes, _T("\\Safebox64.exe"));
			reg.DeleteValue(szExes);
//#else
			lstrcpy(szExex, szExeDir);
			lstrcat(szExex, _T("\\Safebox.exe"));
			reg.DeleteValue(szExex);
//#endif
			reg.Close();
		}

		if (ERROR_SUCCESS == reg.Open(HKEY_LOCAL_MACHINE, _T("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run"), KEY_WRITE)) {
//#if (_WIN64)
			reg.DeleteValue(_T("Safebox64"));
//#else
			reg.DeleteValue(_T("SafeboxXP"));
//#endif
			reg.Close();
		}
		/*
		if (ERROR_SUCCESS == reg.Open(HKEY_CURRENT_USER, _T("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run"), KEY_WRITE)) {
//#if (_WIN64)
			reg.DeleteValue(_T("Safebox64"));
//#else
			reg.DeleteValue(_T("SafeboxXP"));
//#endif
			reg.Close();
		}
		*/
		if ( ERROR_SUCCESS == 
			reg.Open( HKEY_CLASSES_ROOT, _T("*\\ShellEx\\ContextMenuHandlers"), KEY_WRITE ) )
		{
			reg.DeleteSubKey(_T("S3ShlExt"));
			reg.Close();
		}

		if ( ERROR_SUCCESS == 
			reg.Open ( HKEY_CLASSES_ROOT, _T("Directory\\ShellEx\\ContextMenuHandlers"), KEY_WRITE ) )
		{
			reg.DeleteSubKey(_T("S3ShlExt"));
			reg.Close();
		}

		if ( ERROR_SUCCESS == 
			reg.Open ( HKEY_LOCAL_MACHINE, 
			_T("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\ShellIconOverlayIdentifiers"), 
			KEY_WRITE ) )
		{
			reg.DeleteSubKey(_T("0 SafeboxIcon"));
			reg.DeleteSubKey(_T("0 SafeboxIcon1"));
			reg.Close();
		}

		if ( ERROR_SUCCESS == 
			reg.Open ( HKEY_CLASSES_ROOT, _T("CLSID"), KEY_WRITE ) )
		{
			reg.RecurseDeleteKey(S3ExtCLSID);
			reg.RecurseDeleteKey(S3IconCLSID);
			reg.RecurseDeleteKey(S3EncIconCLSID);
			reg.Close();
		}	

		//Remove Safebox app setting
		if ( ERROR_SUCCESS == 
			reg.Open ( HKEY_LOCAL_MACHINE, _T("Software"), KEY_WRITE ) )
		{
			reg.RecurseDeleteKey(_T("Safebox"));
			reg.Close();
		}

#if (_WIN64)
		if ( ERROR_SUCCESS == 
			reg.Open ( HKEY_CLASSES_ROOT, _T("Wow6432Node\\CLSID"), KEY_WRITE ) )
		{
			reg.RecurseDeleteKey(S3ExtCLSID);
			reg.RecurseDeleteKey(S3IconCLSID);
			reg.RecurseDeleteKey(S3EncIconCLSID);
			reg.Close();
		}	
#endif
	}
}

void SetupSyncFolder(TCHAR* szSyncDir)
{
	CRegKey reg;
	LONG    lRet;

	//if ( 0 == (GetVersion() & 0x80000000) )
	{
		//Setup Safebox app
		lRet= reg.Open(HKEY_LOCAL_MACHINE, _T("Software\\Safebox"), KEY_WRITE );
		if( lRet==ERROR_SUCCESS )
		{
			reg.SetStringValue(_T("SyncPath"), szSyncDir);
			reg.Close();
		}
	}
}


void GetExeDir(HINSTANCE hInst, TCHAR* szExeDir)
{
	TCHAR* p;

	GetModuleFileName(hInst, szExeDir, MAX_PATH);
	p= wcsrchr(szExeDir, '\\');
	if( p!=NULL )
		p[0]= '\0';
}

void GetHomeDir(TCHAR* szHomeDir)
{
	char *pDrive,*pPath;
	int i,iDriveLen,iPathLen;

	lstrcpy(szHomeDir, _T(""));

	pDrive= getenv("HOMEDRIVE");
	pPath= getenv("HOMEPATH");

	if( pDrive!=NULL && pPath!=NULL )
	{
		iDriveLen= strlen(pDrive);
		iPathLen= strlen(pPath);
		for(i=0; i<iDriveLen; i++)
			szHomeDir[i]= pDrive[i];
		for(i=0; i<iPathLen; i++)
			szHomeDir[iDriveLen+i]= pPath[i];
		szHomeDir[iDriveLen+i]= '\0';
	}
}

void GetMyDocDir(TCHAR* szDir)
{
	CRegKey reg;
	ULONG lBytes;
	LONG lRet = reg.Open (HKEY_CURRENT_USER, _T("Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders"), KEY_QUERY_VALUE );
	
	lstrcpy(szDir, _T(""));
	if( ERROR_SUCCESS==lRet )
	{
		lBytes= MAX_PATH;
		reg.QueryStringValue (_T("Personal"), szDir, &lBytes);
		reg.Close();
	}

	if( lstrlen(szDir)<4 )
	{
		GetHomeDir(szDir);
		lstrcat(szDir, _T("\\My Documents"));
	}
}

void GetLinkPath(TCHAR* szLinkPath)
{
	GetHomeDir(szLinkPath);
	lstrcat(szLinkPath, _T("\\Links\\Safebox.lnk"));
}

void GetSyncDir(TCHAR* szSyncDir)
{
	TCHAR szCfgPath[MAX_PATH];

	lstrcpy(szSyncDir, _T(""));	
	GetHomeDir(szCfgPath);
	lstrcat(szCfgPath, _T("\\.safebox.dat\\safebox.cfg"));
	if( FileExist(szCfgPath) ) 
	{
		//GetPrivateProfileString(NULL, _T("SafeBoxLocation"), _T(""), szSyncDir, MAX_PATH, szDataPath);		
		CHAR *p,aszBuffer[MAX_PATH];
		DWORD i,dwSize,dwNumRead= 0;
		HANDLE hFile= CreateFile(szCfgPath, GENERIC_READ, FILE_SHARE_READ, 
							NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
		dwSize= sizeof(CHAR)*MAX_PATH;
		memset(aszBuffer, 0, dwSize);
		ReadFile(hFile, aszBuffer, dwSize-1, &dwNumRead,NULL);
		CloseHandle(hFile);
		if( dwNumRead>0 && (p=strstr(aszBuffer, "SafeBoxLocation="))!=NULL )
		{
			p= &p[16];
			i= 0;
			while( p[i]!='\r' && p[i]!='\n' && p[i]!='\0' )
			{
				szSyncDir[i]= p[i];
				i++;
			}

			i= MultiByteToWideChar(GetACP(), 0, p, i, szSyncDir, MAX_PATH); 
			if( i>0 )
				szSyncDir[i]= '\0';
			else 
				szSyncDir[0]= '\0';
		}
	}

	if( lstrlen(szSyncDir)<4 )
	{
		GetMyDocDir(szSyncDir);
		lstrcat(szSyncDir, _T("\\Safebox"));
	}
}

BOOL CheckInstall() {
	CRegKey reg;
	ULONG lBytes;
	LONG lRet;
	BOOL bAppValid= FALSE;
	TCHAR szStartPath[MAX_PATH];
	TCHAR szSyncPath[MAX_PATH];
	TCHAR szVer[64];

	lRet= reg.Open (HKEY_LOCAL_MACHINE, _T("Software\\Safebox"), KEY_QUERY_VALUE );
	if ( ERROR_SUCCESS == lRet )
	{
		memset(szStartPath, 0, sizeof(szStartPath));
		memset(szSyncPath, 0, sizeof(szSyncPath));
		lBytes= sizeof(szStartPath);
		reg.QueryStringValue (_T("InstallPath"), szStartPath, &lBytes);
		lBytes= sizeof(szSyncPath);
		reg.QueryStringValue (_T("SyncPath"), szSyncPath, &lBytes);
		lBytes= sizeof(szVer);
		reg.QueryStringValue(_T(""), szVer, &lBytes);
		reg.Close();
		if( lstrlen(szStartPath)>3 && lstrlen(szSyncPath)>3 && wcscmp(szVer, SafeboxVer)==0 )
		{
			bAppValid= TRUE;
		}
	}
	return bAppValid;
}

void CreateFolderLink(HINSTANCE hInstance, TCHAR* szSyncDir)
{
	TCHAR szExeDir[MAX_PATH],szLinkPath[MAX_PATH];
	BOOL bFolderShortcut= FALSE;

	if( FileExist(szSyncDir)==false ) 
		CreateDirectory(szSyncDir, NULL);

	GetHomeDir(szLinkPath);	
	if( bFolderShortcut ) 
		//To create folder link and remove extension name on IE, use safebox. instead	
		lstrcat(szLinkPath, _T("\\Links\\Safebox."));
	else
		lstrcat(szLinkPath, _T("\\Links\\Safebox.lnk"));

	GetModuleFileName(hInstance, szExeDir, MAX_PATH);
	CreateLink(bFolderShortcut, szSyncDir, szLinkPath, _T("Safebox shortcut"), szExeDir);
}

void RemoveFolderLink()
{
	TCHAR szLinkPath[MAX_PATH],szCmd[MAX_PATH];

	GetHomeDir(szLinkPath);	
	lstrcat(szLinkPath, _T("\\Links\\Safebox"));
	RemoveDir(szLinkPath);

	lstrcat(szLinkPath, _T(".lnk"));
	DeleteFile(szLinkPath);
}

int APIENTRY _tWinMain(HINSTANCE hInstance,
                     HINSTANCE hPrevInstance,
                     LPTSTR    lpCmdLine,
                     int       nCmdShow)
{	
	TCHAR szExeDir[MAX_PATH],*p;
	TCHAR szSyncDir[MAX_PATH];
	bool bDebugMode= false;

	p= wcsstr(lpCmdLine, _T("-d"));
	if( p!=NULL ) {
		bDebugMode= true;	
		OutputDebugString(lpCmdLine);
	}

	p= wcsstr(lpCmdLine, _T("-v"));
	printf("ver 1.2\n");

	// commented by keanu 20121018, from emily's instruction
	//MessageBox(NULL,lpCmdLine, _T("Safebox"), MB_ICONINFORMATION);
	
	GetExeDir(hInstance, szExeDir);	

	if( wcsncmp(lpCmdLine, _T("-install"), 8)==0 )
	{	
		GetSyncDir(szSyncDir);
		Install(szExeDir, szSyncDir);

		if( IsWin7() ) 
			CreateFolderLink(hInstance, szSyncDir);
	}
	else if( wcsncmp(lpCmdLine, _T("-uninstall"), 10)==0 ) 
	{
		RunJava(szExeDir, _T("-exit"));
		Uninstall(szExeDir);

		if( IsWin7() )
			RemoveFolderLink();
	}

	else if( wcsncmp(lpCmdLine, _T("-exit"), 5)==0 ) 
	{
		RunJava(szExeDir, _T("-exit"));
	}

	else if( wcsncmp(lpCmdLine, _T("-move"), 5)==0 ) 
	{
		int nNumArgs= 0;
		LPWSTR* szArg= CommandLineToArgvW(lpCmdLine,&nNumArgs);
		if( szArg!=NULL && nNumArgs>1 )
		{
			lstrcpy(szSyncDir, szArg[1]);
			if( FileExist(szSyncDir) ) 
			{
				SetupSyncFolder(szSyncDir);
				if( IsWin7() ) 
					CreateFolderLink(hInstance, szSyncDir);
			}
		}
	}
	else
	{
		if( CheckInstall()==false ) 
		{
			GetSyncDir(szSyncDir);
			Install(szExeDir, szSyncDir);
		}
		RunJava(szExeDir, lpCmdLine);
	}
	return TRUE;
}

// Message handler for about box.
INT_PTR CALLBACK About(HWND hDlg, UINT message, WPARAM wParam, LPARAM lParam)
{
	UNREFERENCED_PARAMETER(lParam);
	HWND hEdit= GetDlgItem(hDlg, IDC_EDIT1);

	switch (message)
	{
	case WM_INITDIALOG:
		if( hEdit!=NULL )
			SetWindowText(hEdit, _T("A00000"));
		return (INT_PTR)TRUE;

	case WM_COMMAND:
		if (LOWORD(wParam) == IDOK || LOWORD(wParam) == IDCANCEL)
		{
			if( LOWORD(wParam) == IDCANCEL )
				bCancel= TRUE;
			if( hEdit!=NULL )
				GetWindowText(hEdit, gszID, 64);
			EndDialog(hDlg, LOWORD(wParam));
			return (INT_PTR)TRUE;
		}
		break;
	}	
	return (INT_PTR)FALSE;
}

