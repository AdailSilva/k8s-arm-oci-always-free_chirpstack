#!/bin/bash

# Backup dos bancos do ChirpStack v3 (pg_dump local, pelo port-forward).
#
# Os PostgreSQL do v3 sao ClusterIP: o NLB nao abre 5437/5438. Antes de rodar, abra
# o port-forward (mesmas portas locais do 07_port_forward_cluster.txt). Se nada estiver
# escutando na porta, este script abre o port-forward sozinho e o fecha no fim.
#   kubectl port-forward -n chirpstack-v3 svc/chirpstack-v3-ns-postgresql-service 5437:5437
#   kubectl port-forward -n chirpstack-v3 svc/chirpstack-v3-as-postgresql-service 5438:5438

# Verify database owner
#psql --host localhost --port 5437 --username postgres --password --dbname postgres connect_timeout=10
#psql --host localhost --port 5437 --username chirpstack_ns --password --dbname chirpstack_ns connect_timeout=10
#psql --host localhost --port 5438 --username chirpstack_as --password --dbname chirpstack_as connect_timeout=10

# List databases:
#\l

# Exit database:
#\q


# How to pass in password to pg_dump?

# Create a .pgpass file in the home directory of the account that pg_dump will run as
# (modelo: 00.pgpass__move_to_user_directory). The format is:
# hostname:port:database:username:password

# nano ~/.pgpass
# localhost:5437:postgres:postgres:postgres
# localhost:5437:chirpstack_ns:chirpstack_ns:chirpstack_ns
# localhost:5438:chirpstack_as:chirpstack_as:chirpstack_as

# Then, set the file's mode to 0600. Otherwise, it will be ignored.
# chmod 600 ~/.pgpass

# Sem sudo: com sudo o pg_dump procuraria o .pgpass no HOME do root.

# See the Postgresql documentation libpq-pgpass for more details.

# Check contents of backup files
#gunzip -c ~/Apps/OracleCloud/02.k8s-on-arm-oci-always-free_chirpstack/05.chirpstack_v3/00.UsefulScripts/_.backups/00.postgres__*.psql.gz | less
#gunzip -c ~/Apps/OracleCloud/02.k8s-on-arm-oci-always-free_chirpstack/05.chirpstack_v3/00.UsefulScripts/_.backups/01.chirpstack-v3_ns__*.psql.gz | less
#gunzip -c ~/Apps/OracleCloud/02.k8s-on-arm-oci-always-free_chirpstack/05.chirpstack_v3/00.UsefulScripts/_.backups/02.chirpstack-v3_as__*.psql.gz | less

set -o pipefail

NS=chirpstack-v3
SVC_NS=chirpstack-v3-ns-postgresql-service
SVC_AS=chirpstack-v3-as-postgresql-service
PG_HOST=localhost
PG_PORT_NS=5437
PG_PORT_AS=5438
BACKUP_DIR=~/Apps/OracleCloud/02.k8s-on-arm-oci-always-free_chirpstack/05.chirpstack_v3/00.UsefulScripts/_.backups
DATA=$(date +%Y-%m-%d)
PF_PIDS=()

mkdir -p "$BACKUP_DIR"

echo "Requer port-forward: $NS/$SVC_NS $PG_PORT_NS:$PG_PORT_NS e $NS/$SVC_AS $PG_PORT_AS:$PG_PORT_AS."

# Port-forward automatico (so se a porta local estiver livre).
abrir_port_forward() {  # <service> <porta>
  if ! (exec 3<>/dev/tcp/$PG_HOST/$2) 2>/dev/null; then
    echo "Opening port-forward $NS/$1 $2:$2..."
    kubectl -n $NS port-forward svc/$1 $2:$2 >/dev/null 2>&1 &
    PF_PIDS+=($!)
  fi
}
trap '[ ${#PF_PIDS[@]} -gt 0 ] && kill "${PF_PIDS[@]}" 2>/dev/null' EXIT
abrir_port_forward $SVC_NS $PG_PORT_NS
abrir_port_forward $SVC_AS $PG_PORT_AS
[ ${#PF_PIDS[@]} -gt 0 ] && sleep 3


# Database ChirpStack v3 back-up on K8S.
echo "Start back-up ChirpStack-v3..."

# Back-UP database commands:

echo "Enter the password for postgres database Back-UP..."
pg_dump --host $PG_HOST --port $PG_PORT_NS --username postgres --dbname postgres --no-owner | gzip > "$BACKUP_DIR/00.postgres__$DATA.psql.gz"

echo "Enter the password for chirpstack_ns database Back-UP..."
pg_dump --host $PG_HOST --port $PG_PORT_NS --username chirpstack_ns --dbname chirpstack_ns --no-owner | gzip > "$BACKUP_DIR/01.chirpstack-v3_ns__$DATA.psql.gz"

echo "Enter the password for chirpstack_as database Back-UP..."
pg_dump --host $PG_HOST --port $PG_PORT_AS --username chirpstack_as --dbname chirpstack_as --no-owner | gzip > "$BACKUP_DIR/02.chirpstack-v3_as__$DATA.psql.gz"

echo "End back-up ChirpStack-v3..."
