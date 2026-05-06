@echo off
set DIR=%~dp0
cd /d "%DIR%"
set OLLAMA_API_KEY=
java -Xms128m -Xmx128m -Dapp.name="ExpenseTracker" / -jar bin/expensetracker-1.0.0.jar com.delfino.smartypants.Application --spring.config.location=./config/ 2>&1
