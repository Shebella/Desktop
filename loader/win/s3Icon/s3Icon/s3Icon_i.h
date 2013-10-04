

/* this ALWAYS GENERATED file contains the definitions for the interfaces */


 /* File created by MIDL compiler version 7.00.0555 */
/* at Thu May 17 17:58:33 2012
 */
/* Compiler settings for s3Icon.idl:
    Oicf, W1, Zp8, env=Win32 (32b run), target_arch=X86 7.00.0555 
    protocol : dce , ms_ext, c_ext, robust
    error checks: allocation ref bounds_check enum stub_data 
    VC __declspec() decoration level: 
         __declspec(uuid()), __declspec(selectany), __declspec(novtable)
         DECLSPEC_UUID(), MIDL_INTERFACE()
*/
/* @@MIDL_FILE_HEADING(  ) */

#pragma warning( disable: 4049 )  /* more than 64k source lines */


/* verify that the <rpcndr.h> version is high enough to compile this file*/
#ifndef __REQUIRED_RPCNDR_H_VERSION__
#define __REQUIRED_RPCNDR_H_VERSION__ 475
#endif

#include "rpc.h"
#include "rpcndr.h"

#ifndef __RPCNDR_H_VERSION__
#error this stub requires an updated version of <rpcndr.h>
#endif // __RPCNDR_H_VERSION__

#ifndef COM_NO_WINDOWS_H
#include "windows.h"
#include "ole2.h"
#endif /*COM_NO_WINDOWS_H*/

#ifndef __s3Icon_i_h__
#define __s3Icon_i_h__

#if defined(_MSC_VER) && (_MSC_VER >= 1020)
#pragma once
#endif

/* Forward Declarations */ 

#ifndef __Is3IconType_FWD_DEFINED__
#define __Is3IconType_FWD_DEFINED__
typedef interface Is3IconType Is3IconType;
#endif 	/* __Is3IconType_FWD_DEFINED__ */


#ifndef __s3IconType_FWD_DEFINED__
#define __s3IconType_FWD_DEFINED__

#ifdef __cplusplus
typedef class s3IconType s3IconType;
#else
typedef struct s3IconType s3IconType;
#endif /* __cplusplus */

#endif 	/* __s3IconType_FWD_DEFINED__ */


/* header files for imported files */
#include "oaidl.h"
#include "ocidl.h"

#ifdef __cplusplus
extern "C"{
#endif 


#ifndef __Is3IconType_INTERFACE_DEFINED__
#define __Is3IconType_INTERFACE_DEFINED__

/* interface Is3IconType */
/* [unique][nonextensible][dual][uuid][object] */ 


EXTERN_C const IID IID_Is3IconType;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("D80A94BC-1377-4B37-B5CA-F72D3FE9C1B1")
    Is3IconType : public IDispatch
    {
    public:
    };
    
#else 	/* C style interface */

    typedef struct Is3IconTypeVtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE *QueryInterface )( 
            Is3IconType * This,
            /* [in] */ REFIID riid,
            /* [annotation][iid_is][out] */ 
            __RPC__deref_out  void **ppvObject);
        
        ULONG ( STDMETHODCALLTYPE *AddRef )( 
            Is3IconType * This);
        
        ULONG ( STDMETHODCALLTYPE *Release )( 
            Is3IconType * This);
        
        HRESULT ( STDMETHODCALLTYPE *GetTypeInfoCount )( 
            Is3IconType * This,
            /* [out] */ UINT *pctinfo);
        
        HRESULT ( STDMETHODCALLTYPE *GetTypeInfo )( 
            Is3IconType * This,
            /* [in] */ UINT iTInfo,
            /* [in] */ LCID lcid,
            /* [out] */ ITypeInfo **ppTInfo);
        
        HRESULT ( STDMETHODCALLTYPE *GetIDsOfNames )( 
            Is3IconType * This,
            /* [in] */ REFIID riid,
            /* [size_is][in] */ LPOLESTR *rgszNames,
            /* [range][in] */ UINT cNames,
            /* [in] */ LCID lcid,
            /* [size_is][out] */ DISPID *rgDispId);
        
        /* [local] */ HRESULT ( STDMETHODCALLTYPE *Invoke )( 
            Is3IconType * This,
            /* [in] */ DISPID dispIdMember,
            /* [in] */ REFIID riid,
            /* [in] */ LCID lcid,
            /* [in] */ WORD wFlags,
            /* [out][in] */ DISPPARAMS *pDispParams,
            /* [out] */ VARIANT *pVarResult,
            /* [out] */ EXCEPINFO *pExcepInfo,
            /* [out] */ UINT *puArgErr);
        
        END_INTERFACE
    } Is3IconTypeVtbl;

    interface Is3IconType
    {
        CONST_VTBL struct Is3IconTypeVtbl *lpVtbl;
    };

    

#ifdef COBJMACROS


#define Is3IconType_QueryInterface(This,riid,ppvObject)	\
    ( (This)->lpVtbl -> QueryInterface(This,riid,ppvObject) ) 

#define Is3IconType_AddRef(This)	\
    ( (This)->lpVtbl -> AddRef(This) ) 

#define Is3IconType_Release(This)	\
    ( (This)->lpVtbl -> Release(This) ) 


#define Is3IconType_GetTypeInfoCount(This,pctinfo)	\
    ( (This)->lpVtbl -> GetTypeInfoCount(This,pctinfo) ) 

#define Is3IconType_GetTypeInfo(This,iTInfo,lcid,ppTInfo)	\
    ( (This)->lpVtbl -> GetTypeInfo(This,iTInfo,lcid,ppTInfo) ) 

#define Is3IconType_GetIDsOfNames(This,riid,rgszNames,cNames,lcid,rgDispId)	\
    ( (This)->lpVtbl -> GetIDsOfNames(This,riid,rgszNames,cNames,lcid,rgDispId) ) 

#define Is3IconType_Invoke(This,dispIdMember,riid,lcid,wFlags,pDispParams,pVarResult,pExcepInfo,puArgErr)	\
    ( (This)->lpVtbl -> Invoke(This,dispIdMember,riid,lcid,wFlags,pDispParams,pVarResult,pExcepInfo,puArgErr) ) 


#endif /* COBJMACROS */


#endif 	/* C style interface */




#endif 	/* __Is3IconType_INTERFACE_DEFINED__ */



#ifndef __s3IconLib_LIBRARY_DEFINED__
#define __s3IconLib_LIBRARY_DEFINED__

/* library s3IconLib */
/* [version][uuid] */ 


EXTERN_C const IID LIBID_s3IconLib;

EXTERN_C const CLSID CLSID_s3IconType;

#ifdef __cplusplus

class DECLSPEC_UUID("1B649A19-7158-436C-841E-F168CB5C1456")
s3IconType;
#endif
#endif /* __s3IconLib_LIBRARY_DEFINED__ */

/* Additional Prototypes for ALL interfaces */

/* end of Additional Prototypes */

#ifdef __cplusplus
}
#endif

#endif


