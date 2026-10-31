#!/bin/sh
# Nightly backup of Beinema: the database and the uploaded files, kept 7 days in
# /opt/beinema/backups. Installed by setup-server.sh into /etc/cron.daily.
set -eu
cd /opt/beinema
. ./.env
stamp=$(date +%Y%m%d-%H%M)
docker compose exec -T db mysqldump -uroot -p"$DB_ROOT_PASSWORD" --single-transaction --routines beinema \
    | gzip > "backups/db-$stamp.sql.gz"
tar -czf "backups/uploads-$stamp.tar.gz" uploads
find backups -type f -mtime +7 -delete
