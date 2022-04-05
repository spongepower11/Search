@echo off

rem Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
rem or more contributor license agreements. Licensed under the Elastic License
rem 2.0; you may not use this file except in compliance with the Elastic License
rem 2.0.

setlocal enabledelayedexpansion
setlocal enableextensions

set LAUNCHER_TOOLNAME=setup-passwords
set LAUNCHER_LIBS=modules/x-pack-core,modules/x-pack-security
call "%~dp0elasticsearch-cli.bat" ^
  %%* ^
  || goto exit

endlocal
endlocal
:exit
exit /b %ERRORLEVEL%
