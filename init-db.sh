#!/bin/bash
# Wait for SQL Server to start up
echo "Waiting for SQL Server to start..."
for i in {1..50}; do
    /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "StrongPassword!123" -Q "SELECT 1" -C &>/dev/null
    if [ $? -eq 0 ]; then
        echo "SQL Server is ready, executing database.sql..."
        /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "StrongPassword!123" -i /usr/config/database.sql -C
        echo "Database initialized successfully!"
        break
    else
        echo "SQL Server is still starting up..."
        sleep 2
    fi
done
