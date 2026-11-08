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
| `FILE_ENCRYPTION_KEY` | Base64-encoded 32-byte key used to encrypt files at rest (required); generate with `openssl rand -base64 32` |
| `FILE_ENCRYPTION_KEY_ID` | Id of the active encryption key; defaults to `k1` (only needed during rotation) |

The database must be running and reachable before the backend starts. Liquibase applies the database schema during startup. Keep the storage directory persistent and back it up along with PostgreSQL; the database stores file metadata while file contents live on disk.

Do not commit local environment files or real credentials. Keep `JWT_SECRET` and `FILE_ENCRYPTION_KEY` private and stable across restarts.

## Run locally with Maven

Install Java 21 and have a PostgreSQL database available. Set the required environment variables for your database and local storage directory, then run from this repository root.

PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/homecloud"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<your-local-database-password>"
$env:JWT_SECRET = "<your-generated-secret>"
$env:FILE_ENCRYPTION_KEY = "<openssl rand -base64 32 output>"
$env:STORAGE_ROOT = "$PWD\homecloud-storage"
.\mvnw.cmd spring-boot:run
```

Bash:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/homecloud'
export DB_USERNAME='postgres'
export DB_PASSWORD='<your-local-database-password>'
export JWT_SECRET='<your-generated-secret>'
export FILE_ENCRYPTION_KEY='<openssl rand -base64 32 output>'
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
- File contents are encrypted with AES-256-GCM before they are written to disk; see [File encryption at rest](#file-encryption-at-rest).

## File encryption at rest

Files are encrypted end to end at the storage layer so that plaintext contents are never visible to someone with database or filesystem access alone.

### How it works

- Every new upload is encrypted with **AES-256-GCM** (authenticated encryption, 256-bit keys) before it is written to disk, and is decrypted transparently while the download endpoint streams it back to the authorized owner. No plaintext is ever persisted to the storage root, and nothing is stored in the database except metadata (name, size, and key id).
- Encryption and decryption are **streaming**: content is piped through the cipher in small buffers rather than buffered whole in memory, so uploads and downloads use constant memory regardless of file size, and no partially written plaintext ever exists on disk (uploads land as atomic `*.part` -> final-name moves).
- The encrypted payload on disk is a self-describing envelope:
  - `HCE1` magic + version + key id + a fresh random 12-byte IV per file, followed by the GCM ciphertext.
- GCM gives each file an integrity guarantee: any tampering, corruption, or wrong-key attempt is detected when downloading, and the download fails rather than silently returning modified content.

### Key storage and separation

- Keys are supplied through the environment (`FILE_ENCRYPTION_KEY`), never written to disk next to the data, and never stored in the database. The storage root and the database only ever see encrypted bytes.
- Generate a key with:
  ```bash
  openssl rand -base64 32
  ```
  The result must decode to exactly 32 bytes (256 bits). The backend refuses to start when the key is missing, malformed, or the wrong length, so a misconfiguration can never silently result in plaintext being stored.
- Multiple keys can be configured at once (for example `HOMECLOUD_ENCRYPTION_KEYS_K1`, `HOMECLOUD_ENCRYPTION_KEYS_K2` mapped to `homecloud.encryption.keys.*`), with `FILE_ENCRYPTION_KEY_ID` selecting the key used for new files. Old files keep decrypting with their recorded key id.
- Back the key(s) up together with the storage root. **If a key is lost, the files encrypted with it cannot be recovered.**

### Key rotation

Rotation means introducing a new key for new files while keeping the old key available for existing files:

1. Generate a fresh key, e.g. `K2` (`openssl rand -base64 32`).
2. Configure both keys under distinct ids, for example:
   ```properties
   HOMECLOUD_ENCRYPTION_KEYS_K1=<previous FILE_ENCRYPTION_KEY value>
   HOMECLOUD_ENCRYPTION_KEYS_K2=<new key value>
   FILE_ENCRYPTION_KEY_ID=k2
   ```
3. Restart. New uploads are encrypted with `K2`; existing files encrypted with `K1` keep working because their envelope records which key they need.
4. Retire `K1` only when no files reference it anymore:

   ```sql
   SELECT COUNT(*) FROM homecloud.files WHERE encrypted = true AND encryption_key_id = 'k1';
   ```
5. Keep at most one active key id (`FILE_ENCRYPTION_KEY_ID`); all envelopes written while it is active use it, so retired keys must stay configured until no files reference them.

Handle key ids as public identifiers: they are written into each envelope header and into the `encryption_key_id` metadata column, but they contain no secret material.

### Existing files and migration

Files stored before this feature is deployed are still readable: downloads serve those plaintext legacy files as-is (the `files.encrypted` column marks the difference, defaulting `false`). New uploads are always encrypted. Legacy content can be migrated by downloading and re-uploading it through the API; there is no automatic bulk migration.

### Recovery procedures

- **Temporarily rotated key misconfigured (old key dropped too early):** restore the retired key under its original id and restart; all affected files become readable again immediately because envelopes record the key id they were encrypted with.
- **Key permanently lost:** files encrypted with that key are not recoverable. This is why the key backup and the storage backup should be treated as an inseparable pair.
- **Suspected tampering:** affected downloads fail with an integrity error; compare the stored envelope with an intact backup to investigate which copy changed.

### Tests

The encryption behavior is covered by unit tests that run without a database:

- `com.homecloud.crypto.FileEncryptorTest` — round trips, streaming reads, envelope header checks, key-id recording, unique IVs, plaintext-leak detection, tampering, truncation, wrong key, unknown key id, and non-envelope rejection failures.
- `com.homecloud.crypto.FileEncryptionKeyProviderTest` — key resolution, rotation setup, and fail-fast startup validation for every misconfiguration.
- `com.homecloud.file.FileStorageServiceTest` — files are on disk encrypted (never plaintext), decrypt transparently on load, correct plaintext content length, legacy passthrough, cleanup of temporary files on failure, and path traversal protection.
- `com.homecloud.file.FileServiceTest` — upload persists encryption metadata, download decrypts encrypted files and passes legacy files through, and permanent deletion removes encrypted content.




