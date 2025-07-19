# Bitespeed Identity Reconciliation Backend

## Overview
This project solves the Bitespeed identity reconciliation challenge for FluxKart.com. The goal is to link customer identities across multiple purchases, even when they use different emails or phone numbers, by maintaining a unified contact graph in a relational database.

**Scenario:**
Doc Brown (from Back to the Future) buys time machine parts from FluxKart.com using different emails and phone numbers. Bitespeed must reconcile these identities to provide a personalized experience.

---

## Tech Stack
- Java 17
- Spring Boot 3.2.5
- PostgreSQL (tested with 15+)
- Maven
- JPA/Hibernate
- Lombok

---

## Setup Instructions

### 1. Clone the repository
```sh
git clone <repo-url>
cd bitespeed-identity-reconciliation/identity-reconciliation
```

### 2. Configure PostgreSQL
- Ensure PostgreSQL is running locally on port 5432.
- Create a database named `bitespeed`:
  ```sql
  CREATE DATABASE bitespeed;
  ```
- (Optional) Adjust username/password in `src/main/resources/application.properties`:
  ```properties
  spring.datasource.url=jdbc:postgresql://localhost:5432/bitespeed
  spring.datasource.username=postgres
  spring.datasource.password=post@123
  ```

### 3. Build and Run
```sh
mvn clean install
mvn spring-boot:run
```
The server will start on port 8080 by default.

---

## API Documentation

### POST `/identify`
Identify or link a contact based on email and/or phone number.

#### Request Body
```json
{
  "email": "doc@future.com",
  "phoneNumber": "9999999999"
}
```
- At least one of `email` or `phoneNumber` must be provided.

#### Response Body
```json
{
  "contact": {
    "primaryContactId": 1,
    "emails": ["doc@future.com", "emmett@future.com"],
    "phoneNumbers": ["9999999999", "8888888888"],
    "secondaryContactIds": [2, 3]
  }
}
```
- `primaryContactId`: The oldest (primary) contact ID in the group.
- `emails`: All unique emails linked to this identity.
- `phoneNumbers`: All unique phone numbers linked to this identity.
- `secondaryContactIds`: All secondary contact IDs in the group.

#### Example cURL
```sh
curl -X POST http://localhost:8080/identify \
  -H 'Content-Type: application/json' \
  -d '{"email": "doc@future.com", "phoneNumber": "9999999999"}'
```

---

## Data Model

### Contact Table
| Field           | Type      | Description                                      |
|-----------------|-----------|--------------------------------------------------|
| id              | bigint    | Primary key, auto-increment                      |
| phoneNumber     | varchar   | Phone number (nullable)                          |
| email           | varchar   | Email address (nullable)                         |
| linkedId        | varchar   | ID of another Contact (if secondary)             |
| linkPrecedence  | enum      | 'PRIMARY' or 'SECONDARY'                         |
| createdAt       | datetime  | Creation timestamp                               |
| updatedAt       | datetime  | Last update timestamp                            |
| deletedAt       | datetime  | Soft delete timestamp (nullable)                 |

- The oldest contact in a group is PRIMARY; others are SECONDARY and linked via `linkedId`.

---

## Testing
- Tests run against PostgreSQL (not H2).
- The test suite resets the `contact_id_seq` sequence before each test for predictable IDs.
- To run tests:
  ```sh
  mvn test
  ```

---

## Notes
- The application is production-ready for PostgreSQL.
- For any issues, check database connectivity and credentials in `application.properties`.
- The `/identify` endpoint is idempotent and safe to call multiple times for the same input.

---

## License
MIT (or as per your organization) 