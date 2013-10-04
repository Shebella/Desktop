

/* this ALWAYS GENERATED file contains the definitions for the interfaces */


 /* File created by MIDL compiler version 7.00.0555 */
/* at Tue Oct 01 15:31:20 2013
 */
/* Compiler settings for s3Icon1.idl:
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

#ifndef __s3Icon1_i_h__
#define __s3Icon1_i_h__

#if defined(_MSC_VER) && (_MSC_VER >= 1020)
#pragma once
#endif

/* Forward Declarations */ 

#ifndef __Is3Icon1Type_FWD_DEFINED__
#define __Is3Icon1Type_FWD_DEFINED__
typedef interface Is3Icon1Type Is3Icon1Type;
#endif 	/* __Is3Icon1Type_FWD_DEFINED__ */


#ifndef __s3Icon1Type_FWD_DEFINED__
#define __s3Icon1Type_FWD_DEFINED__

#ifdef __cplusplus
typedef class s3Icon1Type s3Icon1Type;
#else
typedef struct s3Icon1Type s3Icon1Type;
#endif /* __cplusplus */

#endif 	/* __s3Icon1Type_FWD_DEFINED__ */


/* header files for imported files */
#include "oaidl.h"
#include "ocidl.h"

#ifdef __cplusplus
extern "C"{
#endif 


#ifndef __Is3Icon1Type_INTERFACE_DEFINED__
#define __Is3Icon1Type_INTERFACE_DEFINED__

/* interface Is3Icon1Type */
/* [unique][nonextensible][dual][uuid][object] */ 


EXTERN_C const IID IID_Is3Icon1Type;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("F9C08B53-2F25-4FD1-99F8-64F7680C1668")
    Is3Icon1Type : public IDispatch
    {
    public:
    };
    
#else 	/* C style interface */

    typedef struct Is3Icon1TypeVtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE *QueryInterface )( 
            Is3Icon1Type * This,
            /* [in] */ REFIID riid,
            /* [annotation][iid_is][out] */ 
            __RPC__deref_out  void **ppvObject);
        
        ULONG ( STDMETHODCALLTYPE *AddRef )( 
            Is3Icon1Type * This);
        
        ULONG ( STDMETHODCALLTYPE *Release )( 
            Is3Icon1Type * This);
        
        HRESULT ( STDMETHODCALLTYPE *GetTypeInfoCount )( 
            Is3Icon1Type * This,
            /* [out] */ UINT *pctinfo);
        
        HRESULT ( STDMETHODCALLTYPE *GetTypeInfo )( 
            Is3Icon1Type * This,
            /* [in] */ UINT iTInfo,
            /* [in] */ LCID lcid,
            /* [out] */ ITypeInfo **ppTInfo);
        
        HRESULT ( STDMETHODCALLTYPE *GetIDsOfNames )( 
            Is3Icon1Type * This,
            /* [in] */ REFIID riid,
            /* [size_is][in] */ LPOLESTR *rgszNames,
            /* [range][in] */ UINT cNames,
            /* [in] */ LCID lcid,
            /* [size_is][out] */ DISPID *rgDispId);
        
        /* [local] */ HRESULT ( STDMETHODCALLTYPE *Invoke )( 
            Is3Icon1Type * This,
            /* [in] */ DISPID dispIdMember,
            /* [in] */ REFIID riid,
            /* [in] */ LCID lcid,
            /* [in] */ WORD wFlags,
            /* [out][in] */ DISPPARAMS *pDispParams,
            /* [out] */ VARIANT *pVarResult,
            /* [out] */ EXCEPINFO *pExcepInfo,
            /* [out] */ UINT *puArgErr);
        
        END_INTERFACE
    } Is3Icon1TypeVtbl;

    interface Is3Icon1Type
    {
        CONST_VTBL struct Is3Icon1TypeVtbl *lpVtbl;
    };

    

#ifdef COBJMACROS


#define Is3Icon1Type_QueryInterface(This,riid,ppvObject)	\
    ( (This)->lpVtbl -> QueryInterface(This,riid,ppvObject) ) 

#define Is3Icon1Type_AddRef(This)	\
    ( (This)->lpVtbl -> AddRef(This) ) 

#define Is3Icon1Type_Release(This)	\
    ( (This)->lpVtbl -> Release(This) ) 


#define Is3Icon1Type_GetTypeInfoCount(This,pctinfo)	\
    ( (This)->lpVtbl -> GetTypeInfoCount(This,pctinfo) ) 

#define Is3Icon1Type_GetTypeInfo(This,iTInfo,lcid,ppTInfo)	\
    ( (This)->lpVtbl -> GetTypeInfo(This,iTInfo,lcid,ppTInfo) ) 

#define Is3Icon1Type_GetIDsOfNames(This,riid,rgszNames,cNames,lcid,rgDispId)	\
    ( (This)->lpVtbl -> GetIDsOfNames(This,riid,rgszNames,cNames,lcid,rgDispId) ) 

#define Is3Icon1Type_Invoke(This,dispIdMember,riid,lcid,wFlags,pDispParams,pVarResult,pExcepInfo,puArgErr)	\
    ( (This)->lpVtbl -> Invoke(This,dispIdMember,riid,lcid,wFlags,pDispParams,pVarResult,pExcepInfo,puArgErr) ) 


#endif /* COBJMACROS */


#endif 	/* C style interface */




#endif 	/* __Is3Icon1Type_INTERFACE_DEFINED__ */



#ifndef __s3Icon1Lib_LIBRARY_DEFINED__
#define __s3Icon1Lib_LIBRARY_DEFINED__

/* library s3Icon1Lib */
/* [version][uuid] */ 


EXTERN_C const IID LIBID_s3Icon1Lib;

EXTERN_C const CLSID CLSID_s3Icon1Type;

#ifdef __cplusplus

class DECLSPEC_UUID("ABC4EA80-B324-4C84-95A7-E8FC767ED68F")
s3Icon1Type;
#endif
#endif /* __s3Icon1Lib_LIBRARY_DEFINED__ */

/* Additional Prototypes for ALL interfaces */

/* end of Additional Prototypes */

#ifdef __cplusplus
}
#endif

#endif


