# Bitespeed Identity Reconciliation Backend

## Overview
This project solves the Bitespeed identity reconciliation challenge for FluxKart.com. The goal is to link customer identities across multiple purchases, even when they use different emails or phone numbers, by maintaining a unified contact graph in a relational database.

## Live API Endpoint

The service is deployed and available at:

**POST** https://bitespeed-identity-reconciliation-xi55.onrender.com/identify

_You can use this endpoint in Postman, curl, or your frontend integration for live testing._

---

## Requirements
- Expose a POST `/identify` endpoint that receives JSON:
  ```json
  {
    "email": "string (optional)",
    "phoneNumber": "string (optional)"
  }
  ```
  - At least one of `email` or `phoneNumber` is required.
- The service links contacts by matching email or phoneNumber, always consolidating under the oldest (primary) contact.
- Returns a consolidated contact group:
  ```json
  {
    "contact": {
      "primaryContactId": number,
      "emails": string[],
      "phoneNumbers": string[],
      "secondaryContactIds": number[]
    }
  }
  ```
- Idempotent: repeated requests with the same data do not create duplicates.
- Robust: Handles all edge cases gracefully.

---

## Sequence Flow
1. User sends request with email and/or phone number.
2. Service checks for existing contacts by email or phone.
3. If matches found, merges or links contacts as needed (oldest becomes primary).
4. If no matches, creates a new primary contact.
5. Returns the consolidated contact group in the response.

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

### 1. Clone the Repository
```sh
git clone https://github.com/<your-username>/bitespeed-identity-reconciliation.git
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
  "email": "doc@future.com", // optional
  "phoneNumber": "9999999999" // optional
}
```
- At least one of `email` or `phoneNumber` is required.

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
- `emails`: All unique emails linked to this identity (primary's email first).
- `phoneNumbers`: All unique phone numbers linked to this identity (primary's phone first).
- `secondaryContactIds`: All secondary contact IDs in the group.

#### Example 400 Bad Request
If both fields are missing or empty:
```json
{
  "error": "Either email or phoneNumber must be provided."
}
```

#### Example cURL
```sh
curl -X POST https://bitespeed-identity-reconciliation-xi55.onrender.com/identify \
  -H 'Content-Type: application/json' \
  -d '{"email": "doc@future.com", "phoneNumber": "9999999999"}'
```

---

## Data Model

### Contact Table
| Field           | Type                        | Description                                                      |
|-----------------|-----------------------------|------------------------------------------------------------------|
| ID              | bigint                      | Primary Key, Auto-Increment                                      |
| PhoneNumber     | varchar                     | Phone Number (nullable)                                          |
| Email           | varchar                     | Email Address (nullable)                                         |
| LinkedId        | varchar                     | ID of another Contact (if secondary)                             |
| LinkPrecedence  | enum ('PRIMARY', 'SECONDARY') | Indicates whether this contact is the master record or linked to one |
| CreatedAt       | datetime                    | Creation Timestamp                                               |
| UpdatedAt       | datetime                    | Last Update Timestamp                                            |
| DeletedAt       | datetime                    | Soft Delete Timestamp (nullable)                                 |

- The oldest contact in a group is PRIMARY; others are SECONDARY and linked via `linkedId`.

---

## Test Cases

| #  | Name/Scenario                        | Request Example | Expected Behavior |
|----|--------------------------------------|-----------------|------------------|
| 1  | New Contact (No Match)               | `{ "email": "jon.snow@winterfell.com", "phoneNumber": "9000000001" }` | Creates new primary |
| 2  | Match by Email                       | `{ "email": "jon.snow@winterfell.com", "phoneNumber": "9000000002" }` | Creates secondary, links to primary |
| 3  | Match by Phone                       | `{ "email": "arya.stark@winterfell.com", "phoneNumber": "9000000002" }` | Creates secondary, links to primary |
| 4  | Full Overlap (Already Exists)        | `{ "email": "jon.snow@winterfell.com", "phoneNumber": "9000000002" }` | No duplicate, returns consolidated group |
| 5  | Multiple Secondary Links             | `{ "email": "daenerys.targaryen@dragonstone.com", "phoneNumber": "9000000002" }` | Adds new secondary, consolidates all |
| 6  | Only Email Provided                  | `{ "email": "tyrion.lannister@casterlyrock.com" }` | Creates new primary or returns consolidated group |
| 7  | Only Phone Provided                  | `{ "phoneNumber": "9000000003" }` | Creates new primary or returns consolidated group |
| 8  | Null Email and Phone                 | `{ "email": null, "phoneNumber": null }` | 400 Bad Request |
| 9  | Same Phone, Different Emails         | `{ "email": "sansa.stark@winterfell.com", "phoneNumber": "9000000003" }` | Adds email to group |
| 10 | Duplicate Entry of Same Person       | `{ "email": "daenerys.targaryen@dragonstone.com", "phoneNumber": "9000000002" }` | No duplicate, returns same primary |

---

## Deployment (Render)
- The app can be deployed to [Render](https://render.com/) with PostgreSQL.
- On push to the main branch, Render auto-builds and redeploys.
- Set environment variables for DB connection as needed.
- See logs in the Render dashboard for deploy status.

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
- If both `email` and `phoneNumber` are missing or empty, the API returns 400 Bad Request (robustness beyond spec).

---

## License
MIT (or as per your organization) 