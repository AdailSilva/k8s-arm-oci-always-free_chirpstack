#!/bin/bash

# Deleting database before restore
# Login to psql again...
#DROP DATABASE database_name; # Replace: database_name

# Troubleshooting
# ERROR: database "database_name" is being accessed by other users
# DETAIL: There is 1 other session using the database.

# Revoke new connections to the database while performing the necessary procedures to delete it
#REVOKE CONNECT ON DATABASE database_name FROM public; # Replace: database_name

# Result: 
# REVOKE

#SELECT pg_terminate_backend(pg_stat_activity.pid) \
#FROM pg_stat_activity \
#WHERE pg_stat_activity.datname = 'database_name'; # Replace: database_name

# DROP database
#DROP DATABASE database_name; # Replace: database_name

# List databases:
#\l

# Exit database:
#\q


# RESTORE database commands:

# First, you must create a new empty database
#CREATE DATABASE database_name TEMPLATE template0; # Replace: database_name

#CREATE DATABASE postgres TEMPLATE template0;
#CREATE DATABASE chirpstack_ns TEMPLATE template0;
#CREATE DATABASE chirpstack_as TEMPLATE template0;
#CREATE DATABASE chirpstack TEMPLATE template0;


# Result: 
# CREATE DATABASE

# List databases:
#\l


set -o pipefail

# Os PostgreSQL do v3 sao ClusterIP: o NLB nao abre 5437/5438. Antes de rodar, abra
# o port-forward (mesmas portas locais do 07_port_forward_cluster.txt). Se nada estiver
# escutando na porta, este script abre o port-forward sozinho e o fecha no fim.
#   kubectl port-forward -n chirpstack-v3 svc/chirpstack-v3-ns-postgresql-service 5437:5437
#   kubectl port-forward -n chirpstack-v3 svc/chirpstack-v3-as-postgresql-service 5438:5438

NS=chirpstack-v3
SVC_NS=chirpstack-v3-ns-postgresql-service
SVC_AS=chirpstack-v3-as-postgresql-service
PG_HOST=localhost
PG_PORT_NS=5437
PG_PORT_AS=5438
BACKUP_DIR=~/Apps/OracleCloud/02.k8s-on-arm-oci-always-free_chirpstack/05.chirpstack_v3/00.UsefulScripts/_.backups
PF_PIDS=()

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


# Database ChirpStack v3 restore on K8S.
echo "Start restore ChirpStack-v3..."

# RESTORE database commands:

echo "Enter the password for postgres database Restore..."
gunzip -c "$BACKUP_DIR"/00.postgres__*.psql.gz | psql --host $PG_HOST --port $PG_PORT_NS --username postgres --dbname postgres

echo "Enter the password for chirpstack_ns database Restore..."
gunzip -c "$BACKUP_DIR"/01.chirpstack-v3_ns__*.psql.gz | psql --host $PG_HOST --port $PG_PORT_NS --username chirpstack_ns --dbname chirpstack_ns

echo "Enter the password for chirpstack_as database Restore..."
gunzip -c "$BACKUP_DIR"/02.chirpstack-v3_as__*.psql.gz | psql --host $PG_HOST --port $PG_PORT_AS --username chirpstack_as --dbname chirpstack_as

echo "End restore ChirpStack-v3..."
