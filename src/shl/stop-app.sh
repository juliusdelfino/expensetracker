#!/bin/bash

PID=$(ps -ef | grep ExpenseTracker | grep -v grep | awk '{print $2}')
kill -15 $PID