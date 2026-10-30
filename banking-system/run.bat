@echo off
if not exist out call build.bat
java -cp out com.bank.Main
