#!/bin/bash

# Backup do banco do ChirpStack v4 (pg_dump local, pelo port-forward).
#
# O PostgreSQL do v4 e ClusterIP: o NLB nao abre 5442. Antes de rodar, abra o
# port-forward (mesma porta local do 07_port_forward_cluster.txt). Se nada estiver
# escutando na porta, este script abre o port-forward sozinho e o fecha no fim.
#   kubectl port-forward -n chirpstack-v4 svc/chirpstack-v4-postgresql-service 5442:5442

# Verify database owner
#psql --host localhost --port 5442 --username postgres --password --dbname postgres connect_timeout=10
#psql --host localhost --port 5442 --username chirpstack --password --dbname chirpstack connect_timeout=10

# List databases:
#\l

# Exit database:
#\q


# How to pass in password to pg_dump?

# Create a .pgpass file in the home directory of the account that pg_dump will run as
# (modelo: 00.pgpass__move_to_user_directory). The format is:
# hostname:port:database:username:password

# nano ~/.pgpass
# localhost:5442:postgres:postgres:postgres
# localhost:5442:chirpstack:chirpstack:chirpstack

# Then, set the file's mode to 0600. Otherwise, it will be ignored.
# chmod 600 ~/.pgpass

# Sem sudo: com sudo o pg_dump procuraria o .pgpass no HOME do root.

# See the Postgresql documentation libpq-pgpass for more details.

# Check contents of backup files
#gunzip -c ~/Apps/OracleCloud/02.k8s-on-arm-oci-always-free_chirpstack/06.chirpstack_v4/00.UsefulScripts/_.backups/00.postgres__*.psql.gz | less
#gunzip -c ~/Apps/OracleCloud/02.k8s-on-arm-oci-always-free_chirpstack/06.chirpstack_v4/00.UsefulScripts/_.backups/01.chirpstack-v4__*.psql.gz | less

set -o pipefail

NS=chirpstack-v4
SVC=chirpstack-v4-postgresql-service
PG_HOST=localhost
PG_PORT=5442
BACKUP_DIR=~/Apps/OracleCloud/02.k8s-on-arm-oci-always-free_chirpstack/06.chirpstack_v4/00.UsefulScripts/_.backups
DATA=$(date +%Y-%m-%d)
PF_PID=""

mkdir -p "$BACKUP_DIR"

echo "Requer port-forward: $NS/$SVC $PG_PORT:$PG_PORT."

# Port-forward automatico (so se a porta local estiver livre).
if ! (exec 3<>/dev/tcp/$PG_HOST/$PG_PORT) 2>/dev/null; then
  echo "Opening port-forward $NS/$SVC $PG_PORT:$PG_PORT..."
  kubectl -n $NS port-forward svc/$SVC $PG_PORT:$PG_PORT >/dev/null 2>&1 &
  PF_PID=$!
  trap '[ -n "$PF_PID" ] && kill $PF_PID 2>/dev/null' EXIT
  sleep 3
fi


# Database ChirpStack v4 back-up on K8S
echo "Start back-up ChirpStack-v4..."

# Back-UP database commands:

echo "Enter the password for postgres database Back-UP..."
pg_dump --host $PG_HOST --port $PG_PORT --username postgres --dbname postgres --no-owner | gzip > "$BACKUP_DIR/00.postgres__$DATA.psql.gz"

echo "Enter the password for chirpstack database Back-UP..."
pg_dump --host $PG_HOST --port $PG_PORT --username chirpstack --dbname chirpstack --no-owner | gzip > "$BACKUP_DIR/01.chirpstack-v4__$DATA.psql.gz"

echo "End back-up ChirpStack-v4..."
