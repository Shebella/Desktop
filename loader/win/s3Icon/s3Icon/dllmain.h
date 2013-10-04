// dllmain.h : Declaration of module class.

class Cs3IconModule : public ATL::CAtlDllModuleT< Cs3IconModule >
{
public :
	DECLARE_LIBID(LIBID_s3IconLib)
	DECLARE_REGISTRY_APPID_RESOURCEID(IDR_S3ICON, "{C6F2D211-6EAD-4D1E-8CCB-727798DFDCF2}")
};

extern class Cs3IconModule _AtlModule;
