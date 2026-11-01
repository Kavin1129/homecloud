# HomeCloud Backend

HomeCloud is a private file storage project you can run on your own computer or server. It is similar to having your own personal cloud drive: people can sign in, upload files, organize them into folders, and retrieve them later.

This repository contains HomeCloud's backend service—the part that handles accounts, files, and storage records. It uses PostgreSQL for application data and a local disk folder for file contents. You can run the service locally with Java 21, PostgreSQL, and the included Maven wrapper.

## Technology

- Java 21 and Spring Boot
- Spring Security with JWT bearer-token authentication
- PostgreSQL, Spring Data JPA, and Liquibase
- Maven

## Configuration

The backend reads these environment variables at startup:

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL for the PostgreSQL database |
| `DB_USERNAME` | PostgreSQL account name |
| `DB_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | Private signing secret for authentication tokens |
| `STORAGE_ROOT` | Root directory for uploaded file data; defaults to `/homecloud-storage` |

The database must be running and reachable before the backend starts. Liquibase applies the database schema during startup. Keep the storage directory persistent and back it up along with PostgreSQL; the database stores file metadata while file contents live on disk.

Do not commit local environment files or real credentials. Keep `JWT_SECRET` private and stable across restarts.

## Run locally with Maven

Install Java 21 and have a PostgreSQL database available. Set the required environment variables for your database and local storage directory, then run from this repository root.

PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/homecloud"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<your-local-database-password>"
$env:JWT_SECRET = "<your-generated-secret>"
$env:STORAGE_ROOT = "$PWD\homecloud-storage"
.\mvnw.cmd spring-boot:run
```

Bash:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/homecloud'
export DB_USERNAME='postgres'
export DB_PASSWORD='<your-local-database-password>'
export JWT_SECRET='<your-generated-secret>'
export STORAGE_ROOT="$PWD/homecloud-storage"
./mvnw spring-boot:run
```

Replace the angle-bracketed values before running. The application uses port `8080` by default. The `dev` Spring profile has a developer-specific database and storage configuration; review `src/main/resources/application-dev.properties` before using that profile on another machine.

## API overview

Unless noted, endpoints require an `Authorization: Bearer <token>` header.

| Route | Purpose |
|---|---|
| `POST /users` | Register a user |
| `POST /auth/login` | Authenticate and return a token |
| `GET /auth/me` | Return the authenticated username |
| `POST /auth/logout` | Revoke the presented token |
| `/files` | List, upload, inspect, download, rename, move, or trash files |
| `/files/trash` | List trashed files; restore or permanently delete them |
| `/folders` | List, create, rename, move, trash, restore, or permanently delete folders |
| `GET /storage` | Return storage usage and the user's quota |

For exact HTTP methods and parameters, see the controllers under `src/main/java/com/homecloud`.

## Authentication and file behavior

- Authentication is stateless and uses bearer tokens. Passwords are hashed with Argon2.
- User registration and login are public. Other application endpoints require authentication and operate on the authenticated user's data.
- File uploads are checked by detected content type. Supported types currently include JPEG, PNG, GIF, WebP, PDF, and plain text.
- Each user has a 30 GiB storage quota. Trashed files continue to count until permanently deleted; uploads that exceed the remaining quota are rejected.
- The maximum multipart file and request sizes are 100 MB.
- File contents are stored under the configured `STORAGE_ROOT`, in per-user directories with generated storage names.




