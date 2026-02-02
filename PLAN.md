# WorldKeeper — Implementation Plan

This is the original plan that was written by Claude Code and approved before any code was written. It's included here as-is (with minor cleanup) to show what the planning phase looked like.

Some details changed during implementation — Java 25 became Java 21, Jetty dependency names were wrong, the Hytale plugin API worked differently than the community docs suggested. The plan was a starting point, not a contract.

---

## Overview

Build a custom Hytale server mod implementing **Grandfather-Father-Son (GFS)** backup retention strategy to replace the simple backup system in AdminUI. This solves the problem of losing restore points when issues go unnoticed beyond the retention window.

### Current Problem
- **AdminUI backup**: Every 30 min, keeps 10 backups = 5 hours of history
- **Backup size**: 304 MB each
- **Risk**: If issue unnoticed for >5 hours, cannot restore to good state

### Solution: GFS Retention
- **Snapshots (frequent)**: Every 30 min, keep 12 (6 hours granularity)
- **Dailies**: One per day, keep 7 (7 days granularity)
- **Archives (weekly)**: One per week, keep 4 (4 weeks granularity)
- **Total**: 23 backups (~7 GB) with up to 4 weeks recovery

---

## Technical Architecture

### Technology Stack
- **Language**: Java (targeting server's JVM version)
- **Build**: Maven with maven-shade-plugin
- **Framework**: Hytale Server API (provided scope)
- **Web Server**: Embedded Jetty 12.1.4
- **Config**: JSON with Gson serialization
- **Scheduling**: Java ScheduledExecutorService

### Key Components

1. **GFSBackupPlugin** — Main plugin class, lifecycle management
2. **BackupManager** — Backup creation, restoration, ZIP operations
3. **RetentionPolicy** — GFS algorithm (promotion/cleanup logic)
4. **BackupScheduler** — Timer-based backup execution
5. **BackupIndex** — Metadata tracking (tier, timestamp, size, checksum)
6. **WebServer** — Jetty-based REST API + static UI
7. **HookExecutor** — Pre/post backup command execution

---

## GFS Retention Algorithm

### Core Logic

1. **All new backups start as Snapshot tier**
2. **Promotion to Daily**: When creating new backup, if oldest Snapshot is >=24 hours old, promote it to Daily
3. **Promotion to Archive**: When promoting to Daily, if oldest Daily is >=7 days old, promote it to Archive
4. **Cleanup**: Delete oldest backups exceeding retention counts

### Pseudocode

```
create_backup():
    1. Execute pre-backup hooks
    2. Flush server world to disk
    3. Create ZIP of world folder
    4. Add to index as Snapshot
    5. Apply retention policy
    6. Execute post-backup hooks

apply_retention_policy():
    snapshots = index.get_by_tier(SNAPSHOT).sort_by_age()
    dailies  = index.get_by_tier(DAILY).sort_by_age()
    archives = index.get_by_tier(ARCHIVE).sort_by_age()

    # Promote oldest Snapshot -> Daily if 24h+ old
    if no recent daily exists:
        oldest_snapshot = snapshots.oldest()
        if oldest_snapshot.age >= 24 hours:
            promote(oldest_snapshot, DAILY)

    # Promote oldest Daily -> Archive if 7d+ old
    if no recent archive exists:
        oldest_daily = dailies.oldest()
        if oldest_daily.age >= 7 days:
            promote(oldest_daily, ARCHIVE)

    # Delete excess in each tier
    delete_excess(snapshots, config.snapshot.retentionCount)
    delete_excess(dailies, config.daily.retentionCount)
    delete_excess(archives, config.archive.retentionCount)
```

### Example Timeline

```
Day 1-2:  12 Snapshots accumulate (every 30 min)
Day 3:    Oldest Snapshot promoted to Daily #1
Day 4:    Next oldest promoted to Daily #2
...
Day 7:    Daily #1 promoted to Archive #1
Week 2:   Daily #8 promoted to Archive #2
Week 5:   Archive #1 deleted, Archive #5 created
```

**Steady State (after 4 weeks):**
- 12 Snapshots (last 6 hours, 30-min intervals)
- 7 Dailies (last 7 days)
- 4 Archives (last 4 weeks)
- **Total: 23 backups, ~7 GB**

---

## Web UI Architecture

### REST API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/backups` | GET | List all backups with metadata and config |
| `/api/backups/create` | POST | Trigger manual backup |
| `/api/backups/download/:filename` | GET | Download backup ZIP |
| `/api/backups/restore/:filename` | POST | Restore backup |
| `/api/backups/delete/:filename` | DELETE | Delete backup |

### Frontend

- **Dashboard**: Total backups, total size, last backup time
- **Backup tables**: Separate sections for Archives, Dailies, Snapshots
- **Tier headers**: Show active config (interval + retention count)
- **Config panel**: Collapsible view of the running configuration
- **Actions per backup**: Download, Restore (if enabled), Delete
- **Manual Backup**: Button to trigger immediate backup
- **Auto-refresh**: Poll API every 30 seconds

### Technology
- **Backend**: Jetty servlets (Java)
- **Frontend**: Vanilla HTML/CSS/JavaScript (no build step, no frameworks)
- **Security**: IP whitelist filter on all requests

---

## Project Structure

```
src/main/
├── java/com/gfsbackup/hytale/
│   ├── GFSBackupPlugin.java        # Plugin lifecycle
│   ├── config/
│   │   ├── BackupConfig.java       # Config POJO
│   │   └── ConfigManager.java      # JSON load/save
│   ├── backup/
│   │   ├── BackupManager.java      # Backup create/restore/delete
│   │   ├── ZipUtility.java         # ZIP compression + checksums
│   │   └── HookExecutor.java       # Pre/post hook execution
│   ├── retention/
│   │   ├── BackupTier.java         # SON/FATHER/GRANDFATHER enum
│   │   ├── BackupMetadata.java     # Per-backup metadata
│   │   ├── BackupIndex.java        # Index persistence
│   │   └── RetentionPolicy.java    # GFS promotion + cleanup
│   ├── scheduler/
│   │   └── BackupScheduler.java    # ScheduledExecutorService timer
│   └── web/
│       ├── WebServer.java          # Embedded Jetty setup
│       ├── IPFilter.java           # IP whitelist enforcement
│       └── servlets/               # REST API handlers
└── resources/
    ├── manifest.json               # Hytale mod manifest
    ├── default-config.json         # Default config template
    └── webapp/                     # Web UI (HTML/CSS/JS)
```

---

## Key Design Decisions

| Decision | Chosen | Why | Rejected |
|----------|--------|-----|----------|
| Standalone vs extension | Standalone mod | Cleaner separation, easier maintenance | Extending AdminUI |
| Scheduling | ScheduledExecutorService | Simple, sufficient for intervals | Quartz (overkill) |
| Promotion logic | Time-based | Predictable recovery points | Count-based |
| Web framework | Embedded Jetty + Servlets | Matches existing server mods | Raw HTTP server |
| Frontend | Vanilla JS | No build step, minimal complexity | React/Vue |
| Compression | Default ZIP | Good speed/size balance | Configurable levels |

---

## Pre/Post Backup Hooks

- **Pre-backup hooks** run before world save and ZIP creation
- **Post-backup hooks** run after backup added to index
- **Two types**: Server commands (start with `/` or `say`) vs system commands
- **Variable substitution**: `{{backup_file}}`, `{{backup_filename}}`, `{{backup_tier}}`, `{{backup_size}}`
- **System commands** use `ProcessBuilder` with 30s timeout
- **Server commands** use Hytale's `CommandManager.handleCommand()`
- Continue on hook failure (don't abort backup)

---

## Storage Estimates

| Profile | Snapshots | Dailies | Archives | Total |
|---------|-----------|---------|----------|-------|
| Default | 12 (3.6 GB) | 7 (2.1 GB) | 4 (1.2 GB) | ~7 GB |
| Aggressive | 6 (1.8 GB) | 3 (0.9 GB) | 2 (0.6 GB) | ~3.3 GB |
| Conservative | 24 (7.3 GB) | 14 (4.3 GB) | 8 (2.4 GB) | ~14 GB |
