@echo off
REM ============================================================
REM SOKA Real-World RASA Launcher for Windows
REM Menjalankan demo penjadwalan komputasi awan RASA pada 4 VM AWS (100 Task)
REM ============================================================
powershell -ExecutionPolicy Bypass -File "%~dp0scripts\demo.ps1" -Tasks 100
pause
