# HomeCloud

HomeCloud is a self-hosted private cloud storage application built from scratch.

The project aims to provide a lightweight and secure way to store, organize, and manage personal files on infrastructure that you control.

HomeCloud is being developed as a practical, learning-focused full-stack project, with an emphasis on understanding how authentication, authorization, file storage, database design, API design, and deployment work together in a complete application.

---

## Project Status

**Current Release:** `v0.1.0-alpha.1`

**Status:** Backend foundation complete — frontend development next.

The initial backend API contract has been defined and is ready for frontend integration.

> **Note:** This is an alpha release and is not production-ready.

---


## Current Stack

- Java 21
- Spring Boot
- Spring Security + JWT
- PostgreSQL
- Liquibase
- Maven

## Features

- User registration and login
- JWT authentication and logout
- File upload and download
- Folder management
- File/folder rename and move
- Trash and restore
- Permanent deletion
- Role-based authorization
- Secure filesystem storage

## Project Status

Backend V1 API is implemented and ready for frontend development.

The React frontend is the next phase of development.

## Running Locally

Configure the required environment variables:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `STORAGE_ROOT`

Then run:

```bash
./mvnw spring-boot:run