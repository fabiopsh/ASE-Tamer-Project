#!/bin/sh
# One database and one user per service: a service cannot read another service's data.

for svc in auth players monsters teams encounters history; do
  var=$(echo "${svc}_DB_PASSWORD" | tr '[:lower:]' '[:upper:]')
  eval password=\${$var:-$svc}
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE USER $svc WITH PASSWORD '$password';
CREATE DATABASE $svc OWNER $svc;
REVOKE ALL ON DATABASE $svc FROM PUBLIC;
SQL
done
