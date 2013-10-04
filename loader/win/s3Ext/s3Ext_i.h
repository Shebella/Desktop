

/* this ALWAYS GENERATED file contains the definitions for the interfaces */


 /* File created by MIDL compiler version 7.00.0555 */
/* at Thu May 17 17:58:26 2012
 */
/* Compiler settings for s3Ext.idl:
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

#ifndef __s3Ext_i_h__
#define __s3Ext_i_h__

#if defined(_MSC_VER) && (_MSC_VER >= 1020)
#pragma once
#endif

/* Forward Declarations */ 

#ifndef __IS3ShlExt_FWD_DEFINED__
#define __IS3ShlExt_FWD_DEFINED__
typedef interface IS3ShlExt IS3ShlExt;
#endif 	/* __IS3ShlExt_FWD_DEFINED__ */


#ifndef __S3ShlExt_FWD_DEFINED__
#define __S3ShlExt_FWD_DEFINED__

#ifdef __cplusplus
typedef class S3ShlExt S3ShlExt;
#else
typedef struct S3ShlExt S3ShlExt;
#endif /* __cplusplus */

#endif 	/* __S3ShlExt_FWD_DEFINED__ */


/* header files for imported files */
#include "oaidl.h"
#include "ocidl.h"

#ifdef __cplusplus
extern "C"{
#endif 


#ifndef __IS3ShlExt_INTERFACE_DEFINED__
#define __IS3ShlExt_INTERFACE_DEFINED__

/* interface IS3ShlExt */
/* [unique][nonextensible][dual][uuid][object] */ 


EXTERN_C const IID IID_IS3ShlExt;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("6DFB23D6-095B-4A4B-B55F-87AC3745859E")
    IS3ShlExt : public IDispatch
    {
    public:
    };
    
#else 	/* C style interface */

    typedef struct IS3ShlExtVtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE *QueryInterface )( 
            IS3ShlExt * This,
            /* [in] */ REFIID riid,
            /* [annotation][iid_is][out] */ 
            __RPC__deref_out  void **ppvObject);
        
        ULONG ( STDMETHODCALLTYPE *AddRef )( 
            IS3ShlExt * This);
        
        ULONG ( STDMETHODCALLTYPE *Release )( 
            IS3ShlExt * This);
        
        HRESULT ( STDMETHODCALLTYPE *GetTypeInfoCount )( 
            IS3ShlExt * This,
            /* [out] */ UINT *pctinfo);
        
        HRESULT ( STDMETHODCALLTYPE *GetTypeInfo )( 
            IS3ShlExt * This,
            /* [in] */ UINT iTInfo,
            /* [in] */ LCID lcid,
            /* [out] */ ITypeInfo **ppTInfo);
        
        HRESULT ( STDMETHODCALLTYPE *GetIDsOfNames )( 
            IS3ShlExt * This,
            /* [in] */ REFIID riid,
            /* [size_is][in] */ LPOLESTR *rgszNames,
            /* [range][in] */ UINT cNames,
            /* [in] */ LCID lcid,
            /* [size_is][out] */ DISPID *rgDispId);
        
        /* [local] */ HRESULT ( STDMETHODCALLTYPE *Invoke )( 
            IS3ShlExt * This,
            /* [in] */ DISPID dispIdMember,
            /* [in] */ REFIID riid,
            /* [in] */ LCID lcid,
            /* [in] */ WORD wFlags,
            /* [out][in] */ DISPPARAMS *pDispParams,
            /* [out] */ VARIANT *pVarResult,
            /* [out] */ EXCEPINFO *pExcepInfo,
            /* [out] */ UINT *puArgErr);
        
        END_INTERFACE
    } IS3ShlExtVtbl;

    interface IS3ShlExt
    {
        CONST_VTBL struct IS3ShlExtVtbl *lpVtbl;
    };

    

#ifdef COBJMACROS


#define IS3ShlExt_QueryInterface(This,riid,ppvObject)	\
    ( (This)->lpVtbl -> QueryInterface(This,riid,ppvObject) ) 

#define IS3ShlExt_AddRef(This)	\
    ( (This)->lpVtbl -> AddRef(This) ) 

#define IS3ShlExt_Release(This)	\
    ( (This)->lpVtbl -> Release(This) ) 


#define IS3ShlExt_GetTypeInfoCount(This,pctinfo)	\
    ( (This)->lpVtbl -> GetTypeInfoCount(This,pctinfo) ) 

#define IS3ShlExt_GetTypeInfo(This,iTInfo,lcid,ppTInfo)	\
    ( (This)->lpVtbl -> GetTypeInfo(This,iTInfo,lcid,ppTInfo) ) 

#define IS3ShlExt_GetIDsOfNames(This,riid,rgszNames,cNames,lcid,rgDispId)	\
    ( (This)->lpVtbl -> GetIDsOfNames(This,riid,rgszNames,cNames,lcid,rgDispId) ) 

#define IS3ShlExt_Invoke(This,dispIdMember,riid,lcid,wFlags,pDispParams,pVarResult,pExcepInfo,puArgErr)	\
    ( (This)->lpVtbl -> Invoke(This,dispIdMember,riid,lcid,wFlags,pDispParams,pVarResult,pExcepInfo,puArgErr) ) 


#endif /* COBJMACROS */


#endif 	/* C style interface */




#endif 	/* __IS3ShlExt_INTERFACE_DEFINED__ */



#ifndef __s3ExtLib_LIBRARY_DEFINED__
#define __s3ExtLib_LIBRARY_DEFINED__

/* library s3ExtLib */
/* [version][uuid] */ 


EXTERN_C const IID LIBID_s3ExtLib;

EXTERN_C const CLSID CLSID_S3ShlExt;

#ifdef __cplusplus

class DECLSPEC_UUID("760669DD-E7AD-4ACD-B921-097B075A7711")
S3ShlExt;
#endif
#endif /* __s3ExtLib_LIBRARY_DEFINED__ */

/* Additional Prototypes for ALL interfaces */

/* end of Additional Prototypes */

#ifdef __cplusplus
}
#endif

#endif


