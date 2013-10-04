1. just click the eclipse.bat to generate the eclipse project file
2. in eclipse, import this project
3. if you see some errors like "Unbound classpath variable: 'M2_REPO/com/jamesmurty/XXX"
please set the M2_REPO variable in your Eclipse:
3.1 Project -> Properties -> Java Build Path -> Libraries, select one of the M2_REPO library
3.2 click Edit -> Variable... -> New... 
3.3 in New Variable Entry, set Name to "M2_REPO", then set Path to your m2 repository folder like "C:/Users/A10138/.m2/repository"
