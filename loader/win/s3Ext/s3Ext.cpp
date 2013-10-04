// s3Ext.cpp : Implementation of DLL Exports.


#include "stdafx.h"
#include "resource.h"
#include "s3Ext_i.h"
#include "dllmain.h"
#include <Windows.h>
#include <atlbase.h>

// Used to determine whether the DLL can be unloaded by OLE.
STDAPI DllCanUnloadNow(void)
{
	return _AtlModule.DllCanUnloadNow();
}

// Returns a class factory to create an object of the requested type.
STDAPI DllGetClassObject(REFCLSID rclsid, REFIID riid, LPVOID* ppv)
{
	return _AtlModule.DllGetClassObject(rclsid, riid, ppv);
}

// DllRegisterServer - Adds entries to the system registry.
STDAPI DllRegisterServer(void)
{
#if 0
CRegKey reg;
	LONG    lRet;

	if ( 0 == (GetVersion() & 0x80000000) )
	{    
		lRet = reg.Open ( HKEY_LOCAL_MACHINE, _T("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Shell Extensions\\Approved"),
							KEY_SET_VALUE );

		if ( ERROR_SUCCESS != lRet )
			return HRESULT_FROM_WIN32(lRet);

		lRet = reg.SetValue ( _T("Test s3 menu extension"),
								_T("{760669DD-E7AD-4ACD-B921-097B075A7711}") );

		if ( ERROR_SUCCESS != lRet )
			return HRESULT_FROM_WIN32(lRet);

		reg.Close();
	}
#endif
	// registers object, typelib and all interfaces in typelib
	//HRESULT hr = _AtlModule.DllRegisterServer();
	HRESULT hr= _AtlModule.RegisterServer(FALSE);

	return hr;
}

// DllUnregisterServer - Removes entries from the system registry.
STDAPI DllUnregisterServer(void)
{
#if 0
	CRegKey reg;
	if ( 0 == (GetVersion() & 0x80000000) )
	{
		if ( ERROR_SUCCESS == 
			reg.Open ( HKEY_LOCAL_MACHINE, _T("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Shell Extensions\\Approved"),
						KEY_SET_VALUE ) )
		{
			reg.DeleteValue ( _T("{760669DD-E7AD-4ACD-B921-097B075A7711}") );
			reg.Close();
		}
	}
#endif
	//HRESULT hr = _AtlModule.DllUnregisterServer();
	HRESULT hr = _AtlModule.UnregisterServer(FALSE);

	return hr;
}

// DllInstall - Adds/Removes entries to the system registry per user per machine.
STDAPI DllInstall(BOOL bInstall, LPCWSTR pszCmdLine)
{
	HRESULT hr = E_FAIL;
	static const wchar_t szUserSwitch[] = L"user";

	if (pszCmdLine != NULL)
	{
		if (_wcsnicmp(pszCmdLine, szUserSwitch, _countof(szUserSwitch)) == 0)
		{
			ATL::AtlSetPerUserRegistration(true);
		}
	}

	if (bInstall)
	{	
		hr = DllRegisterServer();
		if (FAILED(hr))
		{
			DllUnregisterServer();
		}
	}
	else
	{
		hr = DllUnregisterServer();
	}

	return hr;
}


