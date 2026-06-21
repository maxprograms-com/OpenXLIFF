@echo off
pushd "%~dp0" 
set OpenXLIFF_HOME=%CD%
popd
%OpenXLIFF_HOME%\bin\java.exe -XX:+UseCompactObjectHeaders --module-path %OpenXLIFF_HOME%\lib -m openxliff/com.maxprograms.converters.Merge %* 