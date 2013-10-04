// dllmain.h : Declaration of module class.

class Cs3ExtModule : public ATL::CAtlDllModuleT< Cs3ExtModule >
{
public :
	DECLARE_LIBID(LIBID_s3ExtLib)
	DECLARE_REGISTRY_APPID_RESOURCEID(IDR_S3EXT, "{487048D9-C751-44E4-B601-0C02913E5193}")
};

extern class Cs3ExtModule _AtlModule;
