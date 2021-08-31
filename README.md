# Postgres
docker run -p 5432:5432 --name postgres -e POSTGRES_DB=stampy -e POSTGRES_PASSWORD=postgres -d postgres

## Dump and restore

dump:
docker exec -it postgres pg_dump -U postgres stampy > dump_inv_dialog.sql

upload:
docker cp dump_inv_dialog.sql postgres:/dump_inv_dialog.sql

restore:
docker exec -it postgres bash
dropdb -U postgres stampy && createdb -U postgres stampy && psql -U postgres stampy < dump_inv_dialog.sql

# Swagger UI
http://localhost:8080/docs/swagger/index.html
