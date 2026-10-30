@echo off
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > sources.txt
javac -Xlint:all -d out @sources.txt
del sources.txt
echo Build OK -^> out\
