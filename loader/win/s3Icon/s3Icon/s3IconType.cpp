// s3IconType.cpp : Implementation of Cs3IconType

#include "stdafx.h"
#include "s3IconType.h"


// Cs3IconType

#include "stdafx.h"
#include "s3IconType.h"


// CMyOverlayIcon
// IShellIconOverlayIdentifier::GetOverlayInfo
// returns The Overlay Icon Location to the system
STDMETHODIMP Cs3IconType::GetOverlayInfo(
             LPWSTR pwszIconFile,
             int cchMax,
             int* pIndex,
             DWORD* pdwFlags)
{
	// Get our module's full path
	GetModuleFileNameW(_AtlBaseModule.GetModuleInstance(), pwszIconFile, cchMax);

	// Use first icon in the resource
	*pIndex=0; 
	*pdwFlags = ISIOI_ICONFILE | ISIOI_ICONINDEX;
	return S_OK;
}

// IShellIconOverlayIdentifier::GetPriority
// returns the priority of this overlay 0 being the highest. 
STDMETHODIMP Cs3IconType::GetPriority(int* pPriority)
{

	// we want highest priority 
	*pPriority=0;
	return S_OK;
}

// IShellIconOverlayIdentifier::IsMemberOf
// Returns whether the object should have this overlay or not 
STDMETHODIMP Cs3IconType::IsMemberOf(LPCWSTR pwszPath, DWORD dwAttrib)
{
	HRESULT r = S_FALSE;
	TCHAR szPath[MAX_PATH];
	DWORD dw= dwAttrib & FILE_ATTRIBUTE_DIRECTORY;

	if( m_bAppValid==FALSE )
		return S_FALSE;

	if( dw!=FILE_ATTRIBUTE_DIRECTORY )
		return S_FALSE;

	lstrcpy(szPath, pwszPath);
	if( GetPathType(szPath)==0 )
		r= S_OK;

	return r;
}

int Cs3IconType::GetPathType(TCHAR* szPath) 
{
	int iLen= lstrlen(m_szSyncPath);
	int iPathLen= lstrlen(szPath);
	int iType= -1;
	TCHAR ch= _T('');	

	if( lstrlen(szPath)<(iLen-1) )
		return -1;

	//m_szPath must be dir

	if( iPathLen==(iLen-1) && szPath[iPathLen-1]!='\\' )
	{
		lstrcat(szPath, _T("\\"));
	}

	if( lstrcmpi(szPath, m_szSyncPath)==0 )
	{
		iType= 0;
	}

	return iType;
}
