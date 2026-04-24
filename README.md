# Quiz Leaderboard System

**Internship Assignment — SRM · Bajaj Finserv Health**
**Registration No:** RA2311026010701

---

## Problem Overview

A backend integration challenge that simulates a real-world distributed system where:

- A quiz validator API serves score events across 10 paginated polls
- The same event data may appear in multiple polls (idempotent delivery)
- The goal is to correctly deduplicate events, aggregate scores, and submit a leaderboard

---

## Solution Approach

### Key Insight — Deduplication

In distributed systems, the same message can be delivered more than once. The fix is to track which events have already been processed using a unique composite key:

```
key = roundId + "||" + participant
```

If the same key appears in a later poll, it is ignored. Only new (unseen) events update the score tally.

### Flow

```
Poll API 10 times (poll=0 to poll=9)
       │
       ▼
For each event in response
       │
       ├── Already seen (roundId + participant)? → SKIP
       │
       └── New event? → Add to seen set → Add score to participant total
               │
               ▼
       Sort participants by totalScore (descending)
               │
               ▼
       POST leaderboard to /quiz/submit (once)
```

---

## How to Run

### Prerequisites

- Java 11 or higher (uses `java.net.http.HttpClient` — no external dependencies)

### Compile

```bash
cd src/main/java
javac QuizLeaderboard.java
```

### Run

```bash
java QuizLeaderboard
```

**Note:** The program takes approximately 50 seconds to complete — it enforces a mandatory 5-second delay between each of the 10 polls, as required by the API.

---

## Output

The program prints progress for every poll, showing how many new events were added and how many duplicates were ignored:

```
[Poll 0] GET .../quiz/messages?regNo=RA2311026010701&poll=0
  Response: {...}
  +2 new event(s), 0 duplicate(s) ignored

  Waiting 5s before next poll...

[Poll 1] GET .../quiz/messages?regNo=RA2311026010701&poll=1
  Response: {...}
  +1 new event(s), 1 duplicate(s) ignored

...

══════════════════════════════
         LEADERBOARD
══════════════════════════════
   1. Alice                100
   2. Bob                   80
──────────────────────────────
  Total score: 180
══════════════════════════════

[Submit] POST .../quiz/submit
[Result] {"isCorrect":true,"isIdempotent":true,...}
```

---

## Project Structure

```
quiz-leaderboard/
└── src/
    └── main/
        └── java/
            └── QuizLeaderboard.java   ← single self-contained file
```

---

## Design Decisions

| Decision | Reason |
|---|---|
| No external JSON library | The assignment is self-contained; using only Java stdlib keeps the project dependency-free |
| `HashSet` for deduplication | O(1) lookup — efficient regardless of how many events are returned |
| `LinkedHashMap` for scores | Maintains insertion order before sorting |
| Single file | Keeps submission simple and reviewable |
| Submit only once | Prevents idempotency issues with the validator |

---

## API Reference

**Base URL:** `https://devapigw.vidalhealthtpa.com/srm-quiz-task`

| Endpoint | Method | Description |
|---|---|---|
| `/quiz/messages` | GET | Fetch events for a given poll index (0–9) |
| `/quiz/submit` | POST | Submit the final leaderboard |

**Poll query params:** `regNo`, `poll` (0–9)

**Deduplication key:** `roundId + participant` (composite)
