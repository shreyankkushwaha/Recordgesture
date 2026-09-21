# Database Schema Documentation

## Database Overview
- **Engine**: SQLite via Room Persistence Library
- **Database Name**: `quick_record_database`
- **Current Version**: `1`

---

## Tables

### Table: `recordings`
Stores metadata and file locations for all saved recordings.

| Column | Type | Nullable | Primary Key | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `INTEGER` | No | Yes (Auto) | Unique recording identifier |
| `title` | `TEXT` | No | No | User-facing display title |
| `filePath` | `TEXT` | No | No | Local filesystem path (`/data/...`) |
| `uriString` | `TEXT` | No | No | Android content URI representation |
| `durationMs` | `INTEGER` | No | No | Total recording duration in milliseconds |
| `fileSize` | `INTEGER` | No | No | File size in bytes |
| `timestamp` | `INTEGER` | No | No | Creation epoch timestamp in milliseconds |
| `isEncrypted` | `INTEGER` | No | No | 1 if AES-256 GCM encrypted, 0 if plaintext |
| `isBackedUp` | `INTEGER` | No | No | 1 if synced to cloud storage, 0 otherwise |
| `backupUrl` | `TEXT` | Yes | No | Cloud storage download/reference URL |

---

## Indexes & Queries
- **Default Sort**: `SELECT * FROM recordings ORDER BY timestamp DESC`
- **Reactive Stream**: Emits `Flow<List<RecordingEntity>>` on any insert, update, or deletion.
