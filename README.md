# Rostly - Backend

Spring Boot backend for Rostly, an online exam platform with role-based access, invitation-driven student onboarding, and built-in proctoring support.

This service is the API and persistence layer used by the companion frontend repository, `rostly-frontend`.

## Features

- JWT-based authentication with access and refresh tokens
- Email verification and invitation-based exam access
- Role-based flows for `ADMIN`, `TEACHER`, and `STUDENT`
- Exam, question, invitation, session, answer, and user management
- Proctoring-related session monitoring, trust score tracking, and violation logging
- File upload support for written answers and stored evidence
- PostgreSQL persistence with Flyway migrations
- OpenAPI / Swagger UI for API exploration

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- Spring Mail
- Maven Wrapper

## Getting Started

### Prerequisites

- Java 21
- PostgreSQL
- A configured SMTP account for email delivery

### 1. Configure environment variables

Copy `.env.example` to `.env` and fill in real values:

```bash
cp .env.example .env
```

Important settings include:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`
- `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD`, `APP_ADMIN_NAME`
- `APP_JWT_SECRET`
- `APP_STORAGE_PHOTOS_DIR`, `APP_STORAGE_ANSWERS_DIR`, `APP_STORAGE_VIOLATIONS_DIR`

Default local database target:

```text
jdbc:postgresql://localhost:5433/rostly
```

### 2. Run the application

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

### 3. Open the API docs

```text
http://localhost:8080/swagger-ui/index.html
```

## Frontend Integration

The intended client for this API is `rostly-frontend`, the React/Vite frontend for teachers, students, and admins.

- Backend default URL: `http://localhost:8080`
- Frontend API client: `rostly-frontend/src/api/axios.ts`
- If you change the backend host or port, update the frontend API base URL to match

## Main Domain Areas

- `auth`: registration, login, refresh, logout, email verification
- `exam`: exams and exam settings
- `question`: multiple choice, text, and file-upload questions
- `invitation`: teacher-sent student invitations and responses
- `session`: exam sessions, answers, grading, random photos, review flows
- `violation`: proctoring violation capture and evidence access
- `user`: admin user management and profile updates

## Proctoring Signals

The backend supports storing and scoring violations such as:

- tab switching
- copy/paste attempts
- fullscreen exit
- multiple monitors
- developer tools opening
- idle time
- camera or microphone disabled
- screen sharing disabled
- face not visible or multiple faces detected

## Testing

Run the test suite with:

```bash
./mvnw test
```

## Notes

- Flyway migrations run automatically on startup.
- Uploaded files and evidence are stored using the configured local directories.
- The app seeds an initial admin user from the configured `APP_ADMIN_*` values.
