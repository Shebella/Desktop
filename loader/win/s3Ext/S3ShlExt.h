// S3ShlExt.h : Declaration of the CS3ShlExt

#pragma once
#include "resource.h"       // main symbols

#include <shlobj.h>
#include <atlconv.h>
#include <windows.h>
#include "s3Ext_i.h"


#if defined(_WIN32_WCE) && !defined(_CE_DCOM) && !defined(_CE_ALLOW_SINGLE_THREADED_OBJECTS_IN_MTA)
#error "Single-threaded COM objects are not properly supported on Windows CE platform, such as the Windows Mobile platforms that do not include full DCOM support. Define _CE_ALLOW_SINGLE_THREADED_OBJECTS_IN_MTA to force ATL to support creating single-thread COM object's and allow use of it's single-threaded COM object implementations. The threading model in your rgs file was set to 'Free' as that is the only threading model supported in non DCOM Windows CE platforms."
#endif

using namespace ATL;

#define S3_SEL_TYPE_NONE	0
#define S3_SEL_TYPE_FOLDER	1
#define S3_SEL_TYPE_BG		2
#define S3_SEL_TYPE_FILE	3

#define S3_CMD_NONE			0
#define S3_CMD_OPENWEB		1
#define S3_CMD_SHOWINFO		2
#define S3_CMD_MAX			S3_CMD_SHOWINFO

// CS3ShlExt
extern HINSTANCE _hCurInstance;

class ATL_NO_VTABLE CS3ShlExt :
	public CComObjectRootEx<CComSingleThreadModel>,
	public CComCoClass<CS3ShlExt, &CLSID_S3ShlExt>,
	public IShellExtInit,
	public IContextMenu
//	public IDispatch
{
public:
	CS3ShlExt()
	{
	}

DECLARE_REGISTRY_RESOURCEID(IDR_S3SHLEXT)

DECLARE_NOT_AGGREGATABLE(CS3ShlExt)

BEGIN_COM_MAP(CS3ShlExt)
	COM_INTERFACE_ENTRY(IShellExtInit)
	COM_INTERFACE_ENTRY(IContextMenu)
//	COM_INTERFACE_ENTRY(IDispatch)
END_COM_MAP()



	DECLARE_PROTECT_FINAL_CONSTRUCT()

	HRESULT FinalConstruct()
	{
		lstrcpy(m_szPath, _T(""));
		lstrcpy(m_szSyncPath, _T(""));
		lstrcpy(m_szStartPath, _T(""));
		m_nPathType= S3_SEL_TYPE_NONE;
		m_hS3Menu= NULL;
		m_hBmp= LoadBitmap(_hCurInstance, MAKEINTRESOURCE(IDB_BITMAP1) );
		m_bAppValid= FALSE;

		CRegKey reg;
		ULONG lBytes;
		LONG lRet = reg.Open (HKEY_LOCAL_MACHINE, _T("Software\\Safebox"), KEY_QUERY_VALUE );
		if ( ERROR_SUCCESS == lRet )
		{
			lBytes= sizeof(m_szStartPath);
			reg.QueryStringValue (_T("InstallPath"), m_szStartPath, &lBytes);
			lBytes= sizeof(m_szStartPath);
			reg.QueryStringValue (_T("SyncPath"), m_szSyncPath, &lBytes);
			reg.Close();
			if( lstrlen(m_szStartPath)>3 && lstrlen(m_szSyncPath)>3 )
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
		if( m_hS3Menu!=NULL )
			DestroyMenu(m_hS3Menu);
	    if ( NULL != m_hBmp )
			DeleteObject ( m_hBmp );
	}

protected:
	TCHAR m_szPath[MAX_PATH];
	TCHAR m_szSyncPath[MAX_PATH];
	TCHAR m_szStartPath[MAX_PATH];
	UINT m_nPathType;
	HMENU m_hS3Menu;
	HBITMAP m_hBmp;
	BOOL m_bAppValid;
	BOOL CheckPath();
	BOOL RunSafebox(TCHAR* szParam);
public:
	// IShellExtInit
	STDMETHODIMP Initialize(LPCITEMIDLIST, LPDATAOBJECT, HKEY);

	// IContextMenu
	STDMETHODIMP GetCommandString(UINT_PTR, UINT, UINT*, LPSTR, UINT);
	STDMETHODIMP InvokeCommand(LPCMINVOKECOMMANDINFO);
	STDMETHODIMP QueryContextMenu(HMENU, UINT, UINT, UINT, UINT);
};

OBJECT_ENTRY_AUTO(__uuidof(S3ShlExt), CS3ShlExt)
