// dllmain.cpp : Implementation of DllMain.

#include "stdafx.h"
#include "resource.h"
#include "s3Ext_i.h"
#include "dllmain.h"

Cs3ExtModule _AtlModule;
HINSTANCE _hCurInstance= NULL;

// DLL Entry Point
extern "C" BOOL WINAPI DllMain(HINSTANCE hInstance, DWORD dwReason, LPVOID lpReserved)
{
	_hCurInstance= hInstance;
	return _AtlModule.DllMain(dwReason, lpReserved); 
}
