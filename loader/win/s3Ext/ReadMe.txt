========================================================================
    ACTIVE TEMPLATE LIBRARY : s3Ext Project Overview
========================================================================

AppWizard has created this s3Ext project for you to use as the starting point for
writing your Dynamic Link Library (DLL).

This file contains a summary of what you will find in each of the files that
make up your project.

s3Ext.vcxproj
    This is the main project file for VC++ projects generated using an Application Wizard.
    It contains information about the version of Visual C++ that generated the file, and
    information about the platforms, configurations, and project features selected with the
    Application Wizard.

s3Ext.vcxproj.filters
    This is the filters file for VC++ projects generated using an Application Wizard. 
    It contains information about the association between the files in your project 
    and the filters. This association is used in the IDE to show grouping of files with
    similar extensions under a specific node (for e.g. ".cpp" files are associated with the
    "Source Files" filter).

s3Ext.idl
    This file contains the IDL definitions of the type library, the interfaces
    and co-classes defined in your project.
    This file will be processed by the MIDL compiler to generate:
        C++ interface definitions and GUID declarations (s3Ext.h)
        GUID definitions                                (s3Ext_i.c)
        A type library                                  (s3Ext.tlb)
        Marshaling code                                 (s3Ext_p.c and dlldata.c)

s3Ext.h
    This file contains the C++ interface definitions and GUID declarations of the
    items defined in s3Ext.idl. It will be regenerated by MIDL during compilation.

s3Ext.cpp
    This file contains the object map and the implementation of your DLL's exports.

s3Ext.rc
    This is a listing of all of the Microsoft Windows resources that the
    program uses.

s3Ext.def
    This module-definition file provides the linker with information about the exports
    required by your DLL. It contains exports for:
        DllGetClassObject
        DllCanUnloadNow
        DllRegisterServer
        DllUnregisterServer
        DllInstall

/////////////////////////////////////////////////////////////////////////////
Other standard files:

StdAfx.h, StdAfx.cpp
    These files are used to build a precompiled header (PCH) file
    named s3Ext.pch and a precompiled types file named StdAfx.obj.

Resource.h
    This is the standard header file that defines resource IDs.

/////////////////////////////////////////////////////////////////////////////
Proxy/stub DLL project and module definition file:

s3Extps.vcxproj
    This file is the project file for building a proxy/stub DLL if necessary.
	The IDL file in the main project must contain at least one interface and you must
	first compile the IDL file before building the proxy/stub DLL.	This process generates
	dlldata.c, s3Ext_i.c and s3Ext_p.c which are required
	to build the proxy/stub DLL.

s3Extps.vcxproj.filters
    This is the filters file for the proxy/stub project. It contains information about the 
    association between the files in your project and the filters. This association is 
    used in the IDE to show grouping of files with similar extensions under a specific
    node (for e.g. ".cpp" files are associated with the "Source Files" filter).

s3Extps.def
    This module definition file provides the linker with information about the exports
    required by the proxy/stub.

/////////////////////////////////////////////////////////////////////////////
