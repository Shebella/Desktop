
#include <stdio.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdbool.h>
#include <sys/types.h> 
#include <sys/stat.h>
#include <errno.h>

#define MAX_PATH	256

typedef struct {
	char* aszID;
	char* aszAccessKey;
	char* aszSecectKey;	
}USER_INFO; 

bool FileExist(char* filename)
{
	struct stat sts;
	
	if( stat(filename,&sts)==-1 && errno==ENOENT )	
		return false;
	else
		return true;
}

bool DialogBox(char* szID) 
{
	printf("Please key-in ITRI ID:");
	scanf("%s", szID);
	return strlen(szID)>0;
}

bool MakeConfigFile(char* exe_path)
{
	char szExePath[MAX_PATH],*p;
	char aszID[64]= {'\0'};	

	strcpy(szExePath, exe_path);
	p= strchr(szExePath, '/');
	if( p!=NULL )
		strcpy(p, "/safebox.cfg");
	else
		return false;
		
	if( FileExist(szExePath) )
		return true;

	DialogBox(aszID);

	USER_INFO AcceptedUser[]= {	
		//CSS App team
		{ "A00010", "cgNxuQPcZNSOHNTMjLdCRvGJTobDk6qliiy1w", "OsOBxtyfE7Av0sqe9OuPSqdcBqOCYrKpx6BA" },
		{ "A00097", "Kw6pXkHjtd96erEGkTRNWaOP4fVBkAkIbR4g", "D6XPXgpbWfNG6hhiqpQXyKF4fM0JcjyyTOHGg" },
		{ "990527", "HjWqD5IU1VLvpJ1emDnSbT3Kcfrq5vqmJTMnfA", "h1XYIZCbWFfo0knWKocrJBo54BWU7jrI9Q" },
		{ "990500", "OmdeTPI8876zuzfrPzi203xxU9sBJrJn5NogDw", "77jS9zPKWOqKBZUdlgT4IunZT8HHVvdk1Hg" },
		{ "980234", "bebYAJwc6gy3wBPykd2qapn2lzR290ky9AlhOg", "RBKq64yQeSoTHIBmN5APGJBuQ1H6w7VUkY0Ow" },		
		//DMS team
		{ "980206", "ozbJj7VMvkd9nTatGCTSBt3LPQvscwQtTmQ", "l5Acssbx3KS3uCEocecFlqtAjIcyL3psxUYQ" },
		{ "980263", "IyNYPjhrBNc0zcObNlJBzYggIdmLDJNWHYWw", "Jc4ELxxlZPmflbdos5iNf7JD3s53w50nuJyxMg" },
		{ "990158", "HskHY6KimvMMiNzL932vtw1Q0gF6FXqi4ypA", "mk8xJYQ9MIKJnZCT3eDLDnJJiiWQlhvs6Xw" },
		{ "990080", "5tEwT7jurgwdzR5gueJgJN7EypZCyJpZcV5aw", "mImtVlIoDt39zfZw4bL0iR2UexrVnJDudGBMw" },
		{ "990385", "uDp4IOMI4CElX9OCzju29zIvfw6YyR2gLMn8w", "NcYV5pU2fXR7YdeKqepgzcjSmqbVCLtx2hBA" },
		{ "990211", "qs5fSwq49OkC8KDmcLk9moc54Ivc47Tj4XEQ", "kV432ohYl5XnqsoO0OdwNY26qEjUSPTGAkVCg" },
		{ "A00584", "Y4A3mLpUNI7Ac5bhYMlr9Bpsw1zG8jtJMwhw", "sRaz0vKZhfOZQYHVl4dED01UZ97o7d6X5WAQ" },
		{ "A00410", "8OEFa7BGHiRSvchvy7WEqmHsawE80zSTcOlw", "RukMSZ2FvcBpL7fFmQQi4TvNIep1fPsA37dBQ" },

		//PM
		{ "A00006", "H9HpHGJQFhSdiXUxlgNQt2gK6KOVg7FRXTlZKw", "PeILq3wCy3KPF9XDAb8BesLyMDDUuRNEZE7K3g" },
		{ "990055", "MFLatMzqST8XXglXUS5ndZ22pnWqO7rh9VPvQ", "GWdyKQ2j0t63oaXmEgxlfXMJd7sRyFgl7hzKGg" },	
	};

	int i,iSize= sizeof(AcceptedUser)/sizeof(USER_INFO);
	
	for(i=0; i<iSize; i++)
	{
		if( strcmp(aszID,AcceptedUser[i].aszID)==0 )
			break;
	}

	if( i<iSize )
	{
		uint dwBytesWrite;
		char buf[1024];
		FILE *fout;

		fout= fopen(szExePath, "w");

		if( fout!=NULL )
		{		
			dwBytesWrite= sprintf(buf, "HostIP=172.108.61.214\r\n");
			fwrite(buf, dwBytesWrite, 1, fout);
			dwBytesWrite= sprintf(buf, "AccessKeyID=%s\r\n", AcceptedUser[i].aszAccessKey);
			fwrite(buf, dwBytesWrite, 1, fout);
			dwBytesWrite= sprintf(buf, "SecretAccessKey=%s\r\n", AcceptedUser[i].aszSecectKey);
			fwrite(buf, dwBytesWrite, 1, fout);
			dwBytesWrite= sprintf(buf, "DefaultBucket=BKT_%s\r\n", AcceptedUser[i].aszID);
			fwrite(buf, dwBytesWrite, 1, fout);
			dwBytesWrite= sprintf(buf, "SafeBoxLocation=~/Desktop/Safebox");
			fwrite(buf, dwBytesWrite, 1, fout);
			fclose(fout);
			return true;
		}
	}
	else
	{
		printf("Incorrect User!\n");
	}
	
	return false;
}

int RunJava(int argc, char *argv[])
{
	char aszCmd[MAX_PATH],aszParam[MAX_PATH];
	int i,iLen;
	
	strcpy(aszParam, "");
	for(i=1; i<argc; i++)
	{
		strcat(aszParam, argv[i]);
		strcat(aszParam, " ");
	}
	iLen= strlen(aszParam);
	
	if( argc>1 && !strcmp(argv[1], "-install") )
	{
//		system("rm -rf /var/lib/safebox");
//		system("mv ./lib /var/lib/safebox");
	}

//	if( FileExist(_T(".\\jre\\bin\\java.exe")) ) 
//		strcpy(aszCmd, ".\\jre\\bin\\java -cp .\\*; Main ");
//	else
		strcpy(aszCmd, "java -cp ./*.jar: Main ");                        

	strcat(aszCmd, aszParam);
	system(aszCmd);
	return 0;
}

int main(int argc, char *argv[])
{	
//	if( (argc>1 && !strcmp(argv[1], "-h"))  )
		return RunJava(argc, argv);
//	else
//		return 0;
}
