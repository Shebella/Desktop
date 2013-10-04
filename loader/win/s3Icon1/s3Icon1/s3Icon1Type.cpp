// s3Icon1Type.cpp : Implementation of Cs3Icon1Type

#include "stdafx.h"
#include "s3Icon1Type.h"


// Cs3Icon1Type

#include "stdafx.h"
#include "s3Icon1Type.h"


// CMyOverlayIcon
// IShellIconOverlayIdentifier::GetOverlayInfo
// returns The Overlay Icon Location to the system
STDMETHODIMP Cs3Icon1Type::GetOverlayInfo(
             LPWSTR pwszIconFile,
             int cchMax,
             int* pIndex,
             DWORD* pdwFlags)
{
	//OutputDebugStringA("Cs3Icon1Type: GetOverlayInfo\n");
	// Get our module's full path
	GetModuleFileNameW(_AtlBaseModule.GetModuleInstance(), pwszIconFile, cchMax);

	// Use first icon in the resource
	*pIndex=0; 
	*pdwFlags = ISIOI_ICONFILE | ISIOI_ICONINDEX;
	return S_OK;
}

// IShellIconOverlayIdentifier::GetPriority
// returns the priority of this overlay 0 being the highest. 
STDMETHODIMP Cs3Icon1Type::GetPriority(int* pPriority)
{
	//OutputDebugStringA("Cs3Icon1Type: GetPriority\n");

	// we want highest priority 
	*pPriority=0;
	return S_OK;
}

// IShellIconOverlayIdentifier::IsMemberOf
// Returns whether the object should have this overlay or not 
STDMETHODIMP Cs3Icon1Type::IsMemberOf(LPCWSTR pwszPath, DWORD dwAttrib)
{
	HRESULT r = S_FALSE;
	DWORD dw= dwAttrib & FILE_ATTRIBUTE_DIRECTORY;

	if( m_bAppValid==FALSE )
		return S_FALSE;

	if( dw!=FILE_ATTRIBUTE_DIRECTORY )
		return S_FALSE;
	
	//OutputDebugString(pwszPath);

	m_iPathType= GetPathType(pwszPath);
	if( m_iPathType==3 )
		r= S_OK;

	wchar_t *s = _wcsdup(pwszPath);

	if (wcsstr(s, L"codeproject") != 0) {
		r = S_OK;
	}
	//char szDebug[100];
	//sprintf(szDebug, 
	//	"Cs3Icon1Type:IsMemberOf: appValid=%d dw=0x%x m_iPathType=%d\n", m_bAppValid, dw, m_iPathType);
	//OutputDebugStringA(szDebug);

	return r;
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

bool Cs3Icon1Type::FindEncryDir(char* target)
{
	HANDLE hFile; 
	DWORD dwNumRead; 
	bool bFound= false; 
	char szBuffer[1024];
	char *pBeg,*pEnd;
	int iLen;
	TCHAR szCfgPath[MAX_PATH];

	GetHomeDir(szCfgPath);
	lstrcat(szCfgPath, _T("\\.safebox.dat\\safebox.cfg"));

	hFile = CreateFile(szCfgPath, GENERIC_READ, FILE_SHARE_READ, 
                                NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL); 
	if( hFile!=NULL )
	{
		memset(szBuffer, 0, sizeof(szBuffer));
		ReadFile(hFile, szBuffer, 1024, &dwNumRead,NULL); 
		CloseHandle(hFile);
		pBeg= strstr(szBuffer, "EncryptDir=\n");
		iLen= strlen("EncryptDir=\n");

		if( pBeg!=NULL )
		{
			pBeg= pBeg+iLen;
			do
			{
				pEnd= strstr(pBeg, "\n");
				if( pEnd==NULL )
					break;
				pEnd[0]= '\0';
				if( _stricmp(pBeg, target)==0 )
					bFound= true;
				else
					pBeg= pEnd+1;
			} while(!bFound);
		}
	}
	return bFound;
}

int Cs3Icon1Type::GetPathType(CONST TCHAR* pwszPath) 
{
	int iLen= lstrlen(m_szSyncPath);
	int iPathLen= lstrlen(pwszPath);
	int iType= -1;
	int i,iSize;
	TCHAR szPath[MAX_PATH];
	TCHAR status[MAX_PATH];
	TCHAR ch= _T('');
	TCHAR *p;
	char aszPath[MAX_PATH];

	//szPath must be dir here

	if( iPathLen < (iLen-1) )
		return -1;

	lstrcpy(szPath, pwszPath);
	
	if( szPath[iPathLen-1]!='\\' )
		lstrcat(szPath, _T("\\"));

	ch= szPath[iLen];
	szPath[iLen]= '\0';

	//check if match root dir
	if( lstrcmpi(szPath, m_szSyncPath)==0 )
	{
		iType= 0;
		if( ch!= '\0' )
		{				
			//check if match first layer dir 
			szPath[iLen]= ch;
			iPathLen= lstrlen(szPath);
			if( szPath[iPathLen-1]=='\\' )
				szPath[iPathLen-1]= '\0';
			p= wcschr(&szPath[iLen+1], '\\');
			if( p==NULL )
			{
				iType= 1;
				p= &szPath[iLen];
				//todo: handle unicode
				iSize= lstrlen(p);
				for(i=0; i<iSize; i++)
					aszPath[i]= (char)p[i];
				aszPath[i]= '\0';
				//check if enc dir
				if( FindEncryDir(aszPath) )
					iType= 2;
			}
		}
	}
	szPath[iLen]= ch;

	return iType;
}