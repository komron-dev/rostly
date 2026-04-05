# Rostly API Specification
Base URL: `/api`
Auth: Bearer JWT in `Authorization` header (except public endpoints)
All timestamps: ISO 8601 (`2025-01-15T10:30:00`)
All IDs: UUID

---

## RBAC Summary
| Role    | Access                                                        |
|---------|---------------------------------------------------------------|
| ADMIN   | Full access to everything                                     |
| TEACHER | Own exams, own students, cannot manage other teachers         |
| STUDENT | Own invitations, own sessions, own profile                    |

---

## 1. Authentication (public)
### POST /api/auth/register
Registers a new user. Sends verification email.
**Request:**
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "password123",
  "role": "STUDENT"  // STUDENT or TEACHER only
}
```
**Response: 202 Accepted** (empty body)

---

### GET /api/auth/verify?token={token}
Verifies email from the link sent after registration.
For STUDENT → account activated immediately.
For TEACHER → account pending admin approval.
**Response: 200 OK**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "uuid"
}
```
**Errors:**
- `400` — invalid or expired token

---

### POST /api/auth/login
**Request:**
```json
{
  "email": "john@example.com",
  "password": "password123"
}
```
**Response: 200 OK**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "uuid"
}
```
**Errors:**
- `400` — invalid credentials
- `403` — email not verified or account not approved

---

### POST /api/auth/refresh
**Request:**
```json
{
  "refreshToken": "uuid"
}
```
**Response: 200 OK**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "uuid"
}
```
**Errors:**
- `400` — invalid or expired refresh token

---

## 2. Profile (all roles)
### GET /api/profile
Returns current authenticated user's profile.
**Response: 200 OK**
```json
{
  "id": "uuid",
  "name": "John Doe",
  "email": "john@example.com",
  "role": "STUDENT",
  "verified": true,
  "createdAt": "2025-01-15T10:00:00"
}
```

---

### PATCH /api/profile
Updates current user's name or password. Email cannot be changed.
**Request:**
```json
{
  "name": "John Updated",       // optional
  "currentPassword": "old123",  // required if changing password
  "newPassword": "new456"       // optional
}
```
**Response: 200 OK** (updated profile, same shape as GET /profile)
**Errors:**
- `400` — validation failure
- `400` — currentPassword incorrect

---

### DELETE /api/profile
Deletes current user's own account.
**Response: 204 No Content**

---

## 3. Users (ADMIN + TEACHER)
### GET /api/users
- ADMIN: lists all users (teachers + students)
- TEACHER: lists only students

**Query params:**
- `role` — filter by role: `STUDENT`, `TEACHER`
- `verified` — filter by verified: `true`, `false`
- `search` — search by name or email
- `page` — page number (default: 0)
- `size` — page size (default: 20)
- `sort` — field to sort by: `name`, `createdAt` (default: `createdAt`)
- `direction` — `asc`, `desc` (default: `desc`)

**Response: 200 OK**
```json
{
  "content": [
    {
      "id": "uuid",
      "name": "Jane Smith",
      "email": "jane@example.com",
      "role": "STUDENT",
      "verified": true,
      "createdAt": "2025-01-15T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

---

### GET /api/users/{userId}
- ADMIN: any user
- TEACHER: only students

**Response: 200 OK** (same shape as single user above)
**Errors:**
- `404` — user not found
- `403` — teacher trying to access another teacher

---

### DELETE /api/users/{userId}
ADMIN only.
**Response: 204 No Content**
**Errors:**
- `403` — not admin
- `404` — user not found

---

### POST /api/users/{userId}/approve
ADMIN only. Approves a teacher account after email verification.
**Response: 200 OK**
```json
{
  "message": "User approved successfully"
}
```
**Errors:**
- `403` — not admin
- `400` — user is already approved
- `404` — user not found

---

## 4. Exams (TEACHER + ADMIN)
### POST /api/exams
TEACHER creates an exam. `createdBy` is set from JWT.
**Request:**
```json
{
  "name": "Midterm Exam",
  "description": "Covers chapters 1-5",
  "timeLimitMinutes": 90,
  "startTime": "2025-06-01T09:00:00",
  "endTime": "2025-06-01T12:00:00"
}
```
**Response: 201 Created**
```json
{
  "id": "uuid",
  "name": "Midterm Exam",
  "description": "Covers chapters 1-5",
  "timeLimitMinutes": 90,
  "startTime": "2025-06-01T09:00:00",
  "endTime": "2025-06-01T12:00:00",
  "createdBy": { "id": "uuid", "name": "Prof. Smith" },
  "createdAt": "2025-01-15T10:00:00",
  "updatedAt": "2025-01-15T10:00:00"
}
```

---

### GET /api/exams/{examId}
TEACHER sees own exams. ADMIN sees all.
**Response: 200 OK** (same shape as above)
**Errors:**
- `404` — exam not found
- `403` — teacher accessing another teacher's exam

---

### GET /api/exams
**Query params:**
- `search` — search by name
- `page`, `size`, `sort` (`name`, `createdAt`, `startTime`), `direction`

**Response: 200 OK** (paginated list of exams)

---

### PATCH /api/exams/{examId}
Partial update. Only exam creator or admin.
**Request:** (all fields optional)
```json
{
  "name": "Updated Exam Name",
  "timeLimitMinutes": 120
}
```
**Response: 200 OK** (updated exam)
**Errors:**
- `403` — not the creator
- `400` — exam already started, cannot update

---

### DELETE /api/exams/{examId}
Only exam creator or admin.
**Response: 204 No Content**
**Errors:**
- `403` — not the creator
- `400` — exam has active sessions, cannot delete

---

## 5. Exam Settings (TEACHER + ADMIN)
### GET /api/exams/{examId}/settings
**Response: 200 OK**
```json
{
  "id": "uuid",
  "examId": "uuid",
  "requireCamera": false,
  "requireMicrophone": false,
  "allowCopyPaste": true,
  "allowTabSwitch": false,
  "maxIdleSeconds": 300,
  "maxViolations": 5,
  "randomPhotoInterval": 60,
  "createdAt": "2025-01-15T10:00:00",
  "updatedAt": "2025-01-15T10:00:00"
}
```

---

### PUT /api/exams/{examId}/settings
Full replace of settings. Creates if not exists.
**Request:**
```json
{
  "requireCamera": true,
  "requireMicrophone": false,
  "allowCopyPaste": false,
  "allowTabSwitch": false,
  "maxIdleSeconds": 300,
  "maxViolations": 5,
  "randomPhotoInterval": 60
}
```
**Response: 200 OK** (updated settings)
**Errors:**
- `403` — not the exam creator
- `400` — exam already started

---

## 6. Questions (TEACHER + ADMIN)
### POST /api/exams/{examId}/questions
**Request:**
```json
{
  "type": "MULTIPLE_CHOICE",
  "content": "What is the capital of France?",
  "maxPoints": 10.00,
  "orderIndex": 1
}
```
**Response: 201 Created**
```json
{
  "id": "uuid",
  "examId": "uuid",
  "type": "MULTIPLE_CHOICE",
  "content": "What is the capital of France?",
  "maxPoints": 10.00,
  "orderIndex": 1,
  "options": [],
  "createdAt": "2025-01-15T10:00:00",
  "updatedAt": "2025-01-15T10:00:00"
}
```

---

### GET /api/exams/{examId}/questions/{questionId}
**Response: 200 OK** (single question with options)

---

### GET /api/exams/{examId}/questions
**Query params:**
- `type` — filter by `MULTIPLE_CHOICE`, `TEXT`, `FILE_UPLOAD`
- `sort` — `orderIndex`, `createdAt` (default: `orderIndex`)

**Response: 200 OK**
```json
{
  "content": [ ...questions with options... ],
  "totalElements": 10
}
```

---

### PATCH /api/exams/{examId}/questions/{questionId}
**Request:** (all fields optional)
```json
{
  "content": "Updated question text",
  "maxPoints": 15.00,
  "orderIndex": 2
}
```
**Response: 200 OK** (updated question)

---

### DELETE /api/exams/{examId}/questions/{questionId}
**Response: 204 No Content**
**Errors:**
- `400` — exam already started, cannot delete questions

---

## 7. Options (TEACHER + ADMIN)
### POST /api/exams/{examId}/questions/{questionId}/options
**Request:**
```json
{
  "text": "Paris",
  "correct": true
}
```
**Response: 201 Created**
```json
{
  "id": "uuid",
  "questionId": "uuid",
  "text": "Paris",
  "correct": true
}
```

---

### GET /api/exams/{examId}/questions/{questionId}/options
**Response: 200 OK**
```json
[
  { "id": "uuid", "text": "Paris", "correct": true },
  { "id": "uuid", "text": "London", "correct": false }
]
```
Note: `correct` field is hidden from STUDENT role.

---

### GET /api/exams/{examId}/questions/{questionId}/options/{optionId}
**Response: 200 OK** (single option)

---

### PATCH /api/exams/{examId}/questions/{questionId}/options/{optionId}
**Request:**
```json
{
  "text": "Updated option text",
  "correct": false
}
```
**Response: 200 OK** (updated option)

---

### DELETE /api/exams/{examId}/questions/{questionId}/options/{optionId}
**Response: 204 No Content**

---

## 8. Exam Invitations
### POST /api/exams/{examId}/invitations (TEACHER + ADMIN)
Invite one or multiple students.
**Request:**
```json
{
  "studentIds": ["uuid1", "uuid2", "uuid3"]
}
```
**Response: 201 Created**
```json
{
  "invited": 3,
  "alreadyInvited": 0,
  "invitations": [
    {
      "id": "uuid",
      "examId": "uuid",
      "student": { "id": "uuid", "name": "Jane" },
      "sentBy": { "id": "uuid", "name": "Prof. Smith" },
      "status": "SENT",
      "sentAt": "2025-01-15T10:00:00"
    }
  ]
}
```

---

### GET /api/exams/{examId}/invitations (TEACHER + ADMIN)
**Query params:**
- `status` — filter by `SENT`, `ACCEPTED`, `DECLINED`, `EXPIRED`
- `page`, `size`, `sort` (`sentAt`), `direction`

**Response: 200 OK** (paginated list of invitations)

---

### GET /api/invitations (STUDENT)
Lists invitations for the current student.
**Query params:**
- `status` — filter by status
- `page`, `size`

**Response: 200 OK** (paginated list)

---

### GET /api/invitations/{invitationId} (STUDENT)
**Response: 200 OK** (single invitation with exam summary)

---

### PATCH /api/invitations/{invitationId} (STUDENT)
Accept or decline an invitation.
**Request:**
```json
{
  "status": "ACCEPTED"  // ACCEPTED or DECLINED
}
```
**Response: 200 OK** (updated invitation)
**Errors:**
- `400` — invitation already responded to
- `400` — invitation expired

---

## 9. Exam Sessions
### POST /api/exams/{examId}/sessions (STUDENT)
Start an exam session. Student must have an accepted invitation.
**Response: 201 Created**
```json
{
  "id": "uuid",
  "examId": "uuid",
  "status": "IN_PROGRESS",
  "startedAt": "2025-06-01T09:00:00",
  "exam": {
    "name": "Midterm Exam",
    "timeLimitMinutes": 90,
    "endTime": "2025-06-01T12:00:00"
  }
}
```
**Errors:**
- `403` — no accepted invitation
- `400` — exam not started yet or already ended
- `400` — student already has an active session for this exam

---

### GET /api/exams/{examId}/sessions/{sessionId} (STUDENT)
Returns session data. Proctoring fields (`trustScore`, `randomPhotoLocation`) are hidden.
**Response: 200 OK**
```json
{
  "id": "uuid",
  "status": "IN_PROGRESS",
  "startedAt": "2025-06-01T09:00:00",
  "submittedAt": null
}
```

---

### POST /api/exams/{examId}/sessions/{sessionId}/submit (STUDENT)
Submit the exam session.
**Response: 200 OK**
```json
{
  "id": "uuid",
  "status": "SUBMITTED",
  "submittedAt": "2025-06-01T10:30:00"
}
```
**Errors:**
- `400` — session already submitted
- `403` — not the session owner

---

### GET /api/exams/{examId}/sessions (TEACHER + ADMIN)
List all sessions for an exam.
**Query params:**
- `status` — filter by `PENDING`, `IN_PROGRESS`, `SUBMITTED`, `FLAGGED`
- `studentId` — filter by student
- `page`, `size`, `sort` (`startedAt`, `trustScore`), `direction`

**Response: 200 OK**
```json
{
  "content": [
    {
      "id": "uuid",
      "student": { "id": "uuid", "name": "Jane" },
      "status": "SUBMITTED",
      "startedAt": "2025-06-01T09:00:00",
      "submittedAt": "2025-06-01T10:30:00",
      "trustScore": 85,
      "randomPhotoLocation": "/photos/session-uuid/"
    }
  ],
  "totalElements": 30
}
```

---

### GET /api/exams/{examId}/sessions/{sessionId} (TEACHER + ADMIN)
Full session detail including proctoring data.
**Response: 200 OK** (full session with trustScore, randomPhotoLocation)

---

## 10. Questions during session (STUDENT)
### GET /api/exams/{examId}/sessions/{sessionId}/questions
Returns questions without revealing correct answers.
**Response: 200 OK**
```json
[
  {
    "id": "uuid",
    "type": "MULTIPLE_CHOICE",
    "content": "What is the capital of France?",
    "maxPoints": 10.00,
    "orderIndex": 1,
    "options": [
      { "id": "uuid", "text": "Paris" },
      { "id": "uuid", "text": "London" }
    ]
  }
]
```
Note: `correct` field is stripped from options.

---

### POST /api/exams/{examId}/sessions/{sessionId}/questions/{questionId}/answer (STUDENT)
Submit or update an answer. Can be called multiple times before session is submitted.
**Request:**
```json
{
  "selectedOptionId": "uuid",   // for MULTIPLE_CHOICE
  "textAnswer": null,           // for TEXT
  "fileUrl": null               // for FILE_UPLOAD
}
```
**Response: 200 OK**
```json
{
  "id": "uuid",
  "questionId": "uuid",
  "selectedOptionId": "uuid",
  "textAnswer": null,
  "fileUrl": null
}
```
**Errors:**
- `400` — session not in progress
- `400` — question does not belong to this exam
- `400` — wrong answer type for question type

---

## 11. Answers (TEACHER + ADMIN)
### GET /api/exams/{examId}/sessions/{sessionId}/answers
Returns all answers for a session for grading.
**Query params:**
- `questionId` — filter by specific question

**Response: 200 OK**
```json
[
  {
    "id": "uuid",
    "question": { "id": "uuid", "content": "...", "type": "MULTIPLE_CHOICE", "maxPoints": 10 },
    "selectedOption": { "id": "uuid", "text": "Paris", "correct": true },
    "textAnswer": null,
    "fileUrl": null,
    "pointsAwarded": null
  }
]
```

---

### PATCH /api/exams/{examId}/sessions/{sessionId}/answers/{answerId} (TEACHER + ADMIN)
Award points to an answer (manual grading for TEXT/FILE_UPLOAD questions).
**Request:**
```json
{
  "pointsAwarded": 8.50
}
```
**Response: 200 OK** (updated answer)
```

---

Also update the API spec with two additions:

**Grading endpoints to add:**
```
POST /api/exams/{examId}/sessions/{sessionId}/grade
```
TEACHER + ADMIN. Triggers auto-grading for all MULTIPLE_CHOICE answers in the session, then marks session as ready for manual grading.
```
GET /api/exams/{examId}/sessions/{sessionId}/grade-summary

---

## 12. Violations
### POST /api/exams/{examId}/sessions/{sessionId}/violations (STUDENT - system only)
Called automatically by the frontend proctoring system, not manually by student.
**Request:**
```json
{
  "type": "TAB_SWITCH",
  "durationSeconds": 5,
  "evidenceUrl": "/evidence/screenshot.jpg"
}
```
**Response: 201 Created**
```json
{
  "id": "uuid",
  "type": "TAB_SWITCH",
  "occurredAt": "2025-06-01T09:15:00",
  "penaltyScore": 2
}
```

---

### GET /api/exams/{examId}/sessions/{sessionId}/violations (TEACHER + ADMIN)
**Query params:**
- `type` — filter by violation type
- `sort` — `occurredAt`, `penaltyScore` (default: `occurredAt`)
- `direction` — `asc`, `desc`

**Response: 200 OK**
```json
[
  {
    "id": "uuid",
    "type": "TAB_SWITCH",
    "occurredAt": "2025-06-01T09:15:00",
    "durationSeconds": 5,
    "evidenceUrl": "/evidence/screenshot.jpg",
    "penaltyScore": 2
  }
]
```

---

### GET /api/exams/{examId}/sessions/{sessionId}/violations/{violationId} (TEACHER + ADMIN)
**Response: 200 OK** (single violation)

---

## 13. Student Exams (STUDENT)
### GET /api/student/exams
Lists exams the student has been invited to and accepted.
**Query params:**
- `status` — filter by invitation status: `ACCEPTED`, `PENDING`
- `page`, `size`, `sort` (`startTime`, `name`), `direction`

**Response: 200 OK**
```json
{
  "content": [
    {
      "id": "uuid",
      "name": "Midterm Exam",
      "timeLimitMinutes": 90,
      "startTime": "2025-06-01T09:00:00",
      "endTime": "2025-06-01T12:00:00"
    }
  ],
  "totalElements": 5
}
```

---

### GET /api/student/exams/{examId}
**Response: 200 OK** (exam summary without settings/proctoring details)

---

## Error Response Format (all endpoints)
```json
{
  "error": "Human readable message",
  "field": "fieldName"   // present only for validation errors
}
```

## HTTP Status Code Summary
| Code | Meaning                        |
|------|--------------------------------|
| 200  | OK                             |
| 201  | Created                        |
| 202  | Accepted (async operation)     |
| 204  | No Content (delete success)    |
| 400  | Bad Request / Validation error |
| 401  | Unauthorized (no/invalid JWT)  |
| 403  | Forbidden (wrong role)         |
| 404  | Not Found                      |
| 409  | Conflict (duplicate resource)  |
