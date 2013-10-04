// s3Icon1Type.h : Declaration of the Cs3Icon1Type

#pragma once
#include "resource.h"       // main symbols



#include "s3Icon1_i.h"

#include <shlobj.h>
#include <comdef.h>


#if defined(_WIN32_WCE) && !defined(_CE_DCOM) && !defined(_CE_ALLOW_SINGLE_THREADED_OBJECTS_IN_MTA)
#error "Single-threaded COM objects are not properly supported on Windows CE platform, such as the Windows Mobile platforms that do not include full DCOM support. Define _CE_ALLOW_SINGLE_THREADED_OBJECTS_IN_MTA to force ATL to support creating single-thread COM object's and allow use of it's single-threaded COM object implementations. The threading model in your rgs file was set to 'Free' as that is the only threading model supported in non DCOM Windows CE platforms."
#endif

using namespace ATL;


// Cs3Icon1Type

class ATL_NO_VTABLE Cs3Icon1Type :
	public CComObjectRootEx<CComSingleThreadModel>,
	public CComCoClass<Cs3Icon1Type, &CLSID_s3Icon1Type>,
	public IShellIconOverlayIdentifier, 
	public IDispatchImpl<Is3Icon1Type, &IID_Is3Icon1Type, &LIBID_s3Icon1Lib, /*wMajor =*/ 1, /*wMinor =*/ 0>
{
public:
	Cs3Icon1Type()
	{
	}

 // IShellIconOverlayIdentifier Methods
  STDMETHOD(GetOverlayInfo)(LPWSTR pwszIconFile, int cchMax,int *pIndex,DWORD* pdwFlags);
  STDMETHOD(GetPriority)(int* pPriority);
  STDMETHOD(IsMemberOf)(LPCWSTR pwszPath,DWORD dwAttrib);

DECLARE_REGISTRY_RESOURCEID(IDR_S3ICON1TYPE)


BEGIN_COM_MAP(Cs3Icon1Type)
	COM_INTERFACE_ENTRY(Is3Icon1Type)
	COM_INTERFACE_ENTRY(IDispatch)
	COM_INTERFACE_ENTRY(IShellIconOverlayIdentifier) 
END_COM_MAP()



	DECLARE_PROTECT_FINAL_CONSTRUCT()

	HRESULT FinalConstruct()
	{
		CRegKey reg;
		ULONG lBytes;
		LONG lRet;
		
		m_bAppValid= FALSE;
		m_iPathType= -1;
		lstrcpy(m_szInstallPath, _T(""));
		lstrcpy(m_szSyncPath, _T(""));

		lRet= reg.Open (HKEY_LOCAL_MACHINE, _T("Software\\Safebox"), KEY_QUERY_VALUE );
		if ( ERROR_SUCCESS == lRet )
		{
			lBytes= sizeof(m_szInstallPath);
			reg.QueryStringValue (_T("InstallPath"), m_szInstallPath, &lBytes);
			lBytes= sizeof(m_szSyncPath);
			reg.QueryStringValue (_T("SyncPath"), m_szSyncPath, &lBytes);
			reg.Close();
			if( lstrlen(m_szInstallPath)>3 && lstrlen(m_szSyncPath)>3 )
			{
				m_bAppValid= TRUE;
				lBytes= lstrlen(m_szSyncPath);
				if( m_szSyncPath[lBytes-1]!='\\' )
					lstrcat(m_szSyncPath, _T("\\"));
			}
		}

		return S_OK;

	}

	void FinalRelease()
	{
	}

public:

private:
	BOOL m_bAppValid;
	TCHAR m_szInstallPath[MAX_PATH];
	TCHAR m_szSyncPath[MAX_PATH];
	int m_iPathType;

	int GetPathType(CONST TCHAR*); 
	bool FindEncryDir(char* target);


};

OBJECT_ENTRY_AUTO(__uuidof(s3Icon1Type), Cs3Icon1Type)
