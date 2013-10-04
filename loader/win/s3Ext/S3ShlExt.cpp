// S3ShlExt.cpp : Implementation of CS3ShlExt

#include "stdafx.h"
#include "S3ShlExt.h"
#include <windows.h>
#include <process.h>
#include <string>  


void DbgPrint(TCHAR* szDebug)
{
	//OutputDebugString(szDebug);	
}

typedef LONG NTSTATUS;  

#ifndef STATUS_SUCCESS 
#define STATUS_SUCCESS ((NTSTATUS)0x00000000L) 
#endif  

#ifndef STATUS_BUFFER_TOO_SMALL 
#define STATUS_BUFFER_TOO_SMALL ((NTSTATUS)0xC0000023L) 
#endif  

std::wstring GetKeyPathFromKKEY(HKEY key)
{
	typedef DWORD (__stdcall *ZwQueryKeyType)(HANDLE KeyHandle,int KeyInformationClass,PVOID KeyInformation,ULONG Length,PULONG ResultLength);

	std::wstring keyPath= _T(""); 

	if( key != NULL )
	{	         
		HMODULE dll = LoadLibrary(L"ntdll.dll");         
		if (dll != NULL) {		
			ZwQueryKeyType func = reinterpret_cast<ZwQueryKeyType>(::GetProcAddress(dll, "ZwQueryKey"));
			if (func != NULL) {
				DWORD size = 0;
				DWORD result = 0; 
				result = func(key, 3, 0, 0, &size);
				if (result == STATUS_BUFFER_TOO_SMALL) 
				{             
					size = size + 2;             
					wchar_t* buffer = new (std::nothrow) wchar_t[size>>1];
					if (buffer != NULL)
					{ 
						result = func(key, 3, buffer, size, &size); 
						if (result == STATUS_SUCCESS)
						{					
							buffer[size/sizeof(wchar_t)]= L'\0';
							keyPath = std::wstring(&buffer[2]); 					
						}
						delete[] buffer;             
					}  
				}
			}
			FreeLibrary(dll);         
		}    	     
	}
	return keyPath; 
} 

BOOL GetSelTypeFromKey(HKEY hProgID, UINT& nType)
{	
	std::wstring sPath= GetKeyPathFromKKEY(hProgID);

	if( sPath.find(L"_CLASSES\\Directory\\Background")!=std::wstring::npos )
		nType= S3_SEL_TYPE_BG;
	else if( sPath.find(L"_CLASSES\\Directory")!=std::wstring::npos )
		nType= S3_SEL_TYPE_FOLDER;
	else if( sPath.find(L"\\REGISTRY\\MACHINE\\SOFTWARE\\Classes\\Directory\\Background")!=std::wstring::npos )
		nType= S3_SEL_TYPE_BG;
	else if( sPath.find(L"\\REGISTRY\\MACHINE\\SOFTWARE\\Classes\\Directory")!=std::wstring::npos )
		nType= S3_SEL_TYPE_FOLDER;
	else if( sPath.find(L"\\REGISTRY\\MACHINE\\SOFTWARE\\Classes\\")!=std::wstring::npos )
		nType= S3_SEL_TYPE_FILE;
	else
		nType= S3_SEL_TYPE_NONE;

	return (nType!=S3_SEL_TYPE_NONE);
}

BOOL CS3ShlExt::CheckPath() 
{
	int iLen= lstrlen(m_szSyncPath);
	TCHAR ch= _T('');
	BOOL bValid= FALSE;

	if( lstrlen(m_szPath)<iLen )
		return FALSE;

	ch= m_szPath[iLen];
	m_szPath[iLen]= _T('\0');
	if( lstrcmpi(m_szPath, m_szSyncPath)==0 )
		bValid= TRUE;
	m_szPath[iLen]= ch;
	return bValid;
}

BOOL CS3ShlExt::RunSafebox(TCHAR* szParam)
{	

//	MessageBox(0, szParam, _T("RunSafebox"), MB_OK);
//	return TRUE;

	STARTUPINFO si;
    PROCESS_INFORMATION pi;
    TCHAR szCmdline[MAX_PATH];

	lstrcpy(szCmdline, TEXT("javaw -cp .\\*; Main "));
	lstrcat(szCmdline, szParam);

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
		0,              // No creation flags
		NULL,           // Use parent's environment block
		m_szStartPath,  // Starting directory 
		&si,            // Pointer to STARTUPINFO structure
		&pi )           // Pointer to PROCESS_INFORMATION structure
       ) 
	{
		printf("CreateProcess failed (%d)./n", GetLastError() );
		return FALSE;
	}

	//Wait until child process exits.
	//WaitForSingleObject( pi.hProcess, INFINITE ); 
	//MessageBox(0, _T("WaitForSingleObject"), _T("Safebox"), MB_OK);

	// Close process and thread handles. 
	CloseHandle( pi.hProcess );
	CloseHandle( pi.hThread );
	
	return TRUE;
}

// CS3ShlExt

STDMETHODIMP CS3ShlExt::Initialize ( 
  LPCITEMIDLIST pidlFolder,
  LPDATAOBJECT pDataObj,
  HKEY hProgID )
{	
	DbgPrint(_T("Initialize"));

	if( m_bAppValid==FALSE )
		return E_INVALIDARG;

	HRESULT hr = SHGetPathFromIDList ( pidlFolder, m_szPath ) ? S_OK : E_INVALIDARG;	

	if( hr!=S_OK && pDataObj!=NULL )
	{
		FORMATETC fmt = { CF_HDROP, NULL, DVASPECT_CONTENT, -1, TYMED_HGLOBAL };
		STGMEDIUM stg = { TYMED_HGLOBAL };
		HDROP hDrop;
 
		// Look for CF_HDROP data in the data object. If there
		// is no such data, return an error back to Explorer.
		if ( FAILED( pDataObj->GetData ( &fmt, &stg ) ))
			hr= E_INVALIDARG;
 
		// Get a pointer to the actual data.
		hDrop = (HDROP) GlobalLock ( stg.hGlobal ); 
		if ( hDrop!=NULL )
		{
			// Sanity check � make sure there is at least one filename.
			UINT uNumFiles = DragQueryFile ( hDrop, 0xFFFFFFFF, NULL, 0 );  
			if ( uNumFiles>0 )
			{ 
				// Get the name of the first file and store it in our
				// member variable m_szPath.
				if ( 0 == DragQueryFile ( hDrop, 0, m_szPath, MAX_PATH ) )
					hr = E_INVALIDARG;
				else
					hr= S_OK;
			}
			GlobalUnlock ( stg.hGlobal );
			ReleaseStgMedium ( &stg );
		}
	}

	if( hr==S_OK ) {		
		if( CheckPath()==FALSE )		
			hr= E_INVALIDARG;	
		else
			GetSelTypeFromKey(hProgID, m_nPathType);
	}

	if( hr==S_OK ) {		
		TCHAR szDebug[MAX_PATH];
		wsprintf(szDebug, _T("[Shell] Initialize: hr=%d  Dir=%s  m_nPathType=%d\n"), hr, m_szPath, m_nPathType);
		DbgPrint(szDebug);	
	}
    return hr;
}

HRESULT CS3ShlExt::QueryContextMenu (
	HMENU hmenu, UINT uMenuIndex, UINT uidFirstCmd,
	UINT uidLastCmd, UINT uFlags )
{
	// If the flags include CMF_DEFAULTONLY then we shouldn't do anything.
	if ( uFlags & CMF_DEFAULTONLY )
		return MAKE_HRESULT ( SEVERITY_SUCCESS, FACILITY_NULL, 0 );
 
	if( m_nPathType!=S3_SEL_TYPE_NONE )
	{
		m_hS3Menu= CreatePopupMenu();
		AppendMenu(m_hS3Menu, MF_STRING, uidFirstCmd+S3_CMD_OPENWEB, _T("&Safebox website"));
		AppendMenu(m_hS3Menu, MF_STRING, uidFirstCmd+S3_CMD_SHOWINFO, _T("&Deatils"));
		InsertMenu(hmenu, uMenuIndex, MF_STRING | MF_POPUP, (UINT)m_hS3Menu, _T("Safebox") );
		if( m_hBmp!=NULL )
			SetMenuItemBitmaps(hmenu, uMenuIndex, MF_BYPOSITION, m_hBmp, m_hBmp);
	}
	return MAKE_HRESULT ( SEVERITY_SUCCESS, FACILITY_NULL, S3_CMD_MAX+1 );
}

HRESULT CS3ShlExt::GetCommandString (
	UINT_PTR idCmd, UINT uFlags, UINT* pwReserved,
	LPSTR pszName, UINT cchMax )
{
	USES_CONVERSION;

	// Check idCmd, it must be 0 since we have only one menu item.
	//if ( 0 != idCmd )
	//	return E_INVALIDARG;
 
	// If Explorer is asking for a help string, copy our string into the supplied buffer.
	if ( uFlags & GCS_HELPTEXT )
	{
		LPCTSTR szText = _T("This is the Safebox shell extension's help"); 
		if ( uFlags & GCS_UNICODE )
		{
			lstrcpynW ( (LPWSTR) pszName, T2CW(szText), cchMax );
		}
		else
		{
			lstrcpynA ( pszName, T2CA(szText), cchMax );
		} 
		return S_OK;
	} 
	return E_INVALIDARG;
}

HRESULT CS3ShlExt::InvokeCommand ( LPCMINVOKECOMMANDINFO pCmdInfo )
{
	TCHAR szMsg[MAX_PATH + 32]; 
	HRESULT hr= S_OK;
	UINT wCmd= LOWORD( pCmdInfo->lpVerb );

	//wsprintf(szMsg, _T("[shell] HI=0x%x command=%d"), HIWORD( pCmdInfo->lpVerb ), LOWORD( pCmdInfo->lpVerb)); 
	//DbgPrint(szMsg);

	// If lpVerb really points to a string, ignore this function call and bail out.
	if ( 0 != HIWORD( pCmdInfo->lpVerb ) )
		return E_INVALIDARG;

	// Get the command index - the only valid one is 0.
	switch ( wCmd )
	{
	case 0:		
		wsprintf ( szMsg, _T("The selected path was:\n\n%s"), m_szPath ); 		
		DbgPrint(szMsg);
		break;
	case S3_CMD_OPENWEB:
		RunSafebox(_T("-web"));
		break;
	case S3_CMD_SHOWINFO:
		wsprintf(szMsg, _T("-info \"%s\""), m_szPath); 
		RunSafebox(szMsg);
		break;
	default:
		hr= E_INVALIDARG;
		break;
	}
	return hr;
}