# WorldKeeper

Smart tiered backup retention for Hytale servers.

## The Problem

The default Hytale backup system (AdminUI) takes a backup every X minutes and keeps the last Y copies. With a typical config of every 30 minutes keeping 10 backups, you get **5 hours** of restore history. If a problem goes unnoticed overnight, your oldest backup is already corrupted.

## How WorldKeeper Fixes This

WorldKeeper uses a tiered retention strategy (Grandfather-Father-Son) that keeps frequent recent backups *and* older restore points:

| Tier | Default Interval | Default Retention | Coverage |
|------|------------------|-------------------|----------|
| **Snapshots** | 30 min | 12 backups | Last 6 hours |
| **Dailies** | Daily | 7 backups | Last 7 days |
| **Archives** | Weekly | 4 backups | Last 4 weeks |

**Total: 23 backups (~7 GB at 304 MB/backup) covering up to 4 weeks of history.**

New backups always start as Snapshots. The oldest Snapshot is promoted to a Daily when it's 24+ hours old, and the oldest Daily is promoted to an Archive when it's 7+ days old. Excess backups in each tier are deleted automatically. All intervals and retention counts are configurable.

## Features

- **Tiered retention** -- Snapshots, Dailies, and Archives with automatic promotion
- **Web dashboard** -- View, download, create, and delete backups from a browser
- **Active config display** -- Web UI shows exactly what's configured so there's no guessing
- **Pre/post hooks** -- Run server commands or shell scripts before and after each backup
- **AdminUI conflict detection** -- Warns at startup if both systems are running
- **Configurable restore** -- Web-based restore is off by default, opt-in via config

## Installation

### Quick Install (Recommended)

1. Download `hytale-gfs-backup-1.0.0.jar` from the [latest release](../../releases/latest)
2. Copy it to your Hytale `Server/mods/` folder
3. Restart the server

That's it. On first startup, WorldKeeper creates its config at `mods/com.gfsbackup_WorldKeeper/config.json`.

### Build from Source

If you want to build the mod yourself instead of using the precompiled JAR:

**Prerequisites:** Java 21+, Maven 3.6+

```bash
# Build the mod
mvn clean package

# Copy to server mods folder
cp target/hytale-gfs-backup-1.0.0.jar /path/to/Server/mods/
```

### Disable AdminUI Backups

WorldKeeper runs independently from the built-in AdminUI backup system. Running both means two systems writing backups to the same `backups/` folder. WorldKeeper logs a warning at startup if it detects AdminUI backups are still enabled.

To disable AdminUI backups, edit `Server/AdminUI/Backup.json`:

```json
{
  "enabled": false
}
```

## Configuration

All settings are in `mods/com.gfsbackup_WorldKeeper/config.json`. Created with defaults on first run.

```json
{
  "enabled": true,
  "backupFolder": "backups",
  "worldFolder": "universe",
  "tiers": {
    "son": {
      "enabled": true,
      "intervalMinutes": 30,
      "retentionCount": 12,
      "description": "30-minute backups for 6 hours"
    },
    "father": {
      "enabled": true,
      "intervalMinutes": 1440,
      "retentionCount": 7,
      "description": "Daily backups for 7 days"
    },
    "grandfather": {
      "enabled": true,
      "intervalMinutes": 10080,
      "retentionCount": 4,
      "description": "Weekly backups for 4 weeks"
    }
  },
  "hooks": {
    "preBackup": [
      "say [WorldKeeper] Starting backup..."
    ],
    "postBackup": [
      "say [WorldKeeper] Backup complete!"
    ]
  },
  "webServer": {
    "enabled": true,
    "port": 8081,
    "allowRestore": false,
    "allowedIPs": []
  },
  "advanced": {
    "serverSaveBeforeBackup": true,
    "deleteEmptyBackups": true,
    "asyncBackup": true
  }
}
```

### Config Reference

| Key | Default | Description |
|-----|---------|-------------|
| `enabled` | `true` | Master switch for the entire mod |
| `backupFolder` | `"backups"` | Directory for backup ZIPs, relative to `Server/` |
| `worldFolder` | `"universe"` | World directory to back up, relative to `Server/` |

#### Tiers

The config uses `son`, `father`, `grandfather` internally. These map to **Snapshots**, **Dailies**, and **Archives** in the web UI.

| Key | Description |
|-----|-------------|
| `enabled` | Enable/disable this tier |
| `intervalMinutes` | How often backups run (son tier drives the scheduler) |
| `retentionCount` | Max backups to keep in this tier |

#### Web Server

| Key | Default | Description |
|-----|---------|-------------|
| `enabled` | `true` | Enable the web UI |
| `port` | `8081` | HTTP port for the web UI |
| `allowRestore` | `false` | Allow restoring backups from the web UI |
| `allowedIPs` | `[]` | IP whitelist (empty = allow all) |

#### Advanced

| Key | Default | Description |
|-----|---------|-------------|
| `serverSaveBeforeBackup` | `true` | Flush world to disk before backup |
| `deleteEmptyBackups` | `true` | Delete backups with 0 bytes |
| `asyncBackup` | `true` | Run backups asynchronously |

## Web UI

Access the dashboard at `http://localhost:8081` (or your configured port).

- Backup tables for each tier with the active config shown in the header (e.g. "Snapshots -- every 30 min, keeping 12")
- Collapsible config panel showing the full active configuration
- Total backup count, size, and last backup time
- Create, download, and delete backups
- Restore backups (when `allowRestore` is enabled)
- Auto-refreshes every 30 seconds

## REST API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/backups` | GET | List all backups with stats and config |
| `/api/backups/create` | POST | Trigger a manual backup |
| `/api/backups/download/:filename` | GET | Download a backup ZIP |
| `/api/backups/restore/:filename` | POST | Restore a backup (requires `allowRestore`) |
| `/api/backups/delete/:filename` | DELETE | Delete a backup |

## Hooks

Hooks run server commands or system commands before and after each backup.

- Lines starting with `say` or `/` are executed as **server commands**
- All other lines are executed as **system commands** via the shell (30s timeout)

### Variable Substitution

| Variable | Description |
|----------|-------------|
| `{{backup_file}}` | Full path to the backup ZIP |
| `{{backup_filename}}` | Filename only (e.g. `2026-02-01_09-31-10.zip`) |
| `{{backup_tier}}` | Tier name (`SON`, `FATHER`, `GRANDFATHER`) |
| `{{backup_size}}` | File size in bytes |

### Examples

```json
{
  "hooks": {
    "preBackup": [
      "say [WorldKeeper] Starting world backup..."
    ],
    "postBackup": [
      "say [WorldKeeper] Complete!",
      "/usr/local/bin/notify-discord.sh 'Backup done: {{backup_filename}} ({{backup_tier}})'"
    ]
  }
}
```

## Restoring a Backup

Backups are ZIP archives of the `universe/` directory containing worlds, chunks, player data, warps, and memories.

**Manual restore process:**

1. Stop the Hytale server
2. Rename or move the current `Server/universe/` directory
3. Extract the backup ZIP into `Server/` (it recreates the `universe/` folder)
4. Start the server

The web UI restore feature (when enabled via `allowRestore`) extracts backups to a `temp-restore/` directory for safety. You still need to manually swap folders and restart.

## Storage Estimates

Storage depends on your world size. With a ~304 MB world:

| Profile | Snapshots | Dailies | Archives | Total |
|---------|-----------|---------|----------|-------|
| Default | 12 (3.6 GB) | 7 (2.1 GB) | 4 (1.2 GB) | **~7 GB** |
| Aggressive | 6 (1.8 GB) | 3 (0.9 GB) | 2 (0.6 GB) | **~3.3 GB** |
| Conservative | 24 (7.3 GB) | 14 (4.3 GB) | 8 (2.4 GB) | **~14 GB** |

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
│       └── servlets/               # REST API handlers
└── resources/
    ├── manifest.json               # Hytale mod manifest
    ├── default-config.json         # Default config template
    └── webapp/                     # Web UI (HTML/CSS/JS)
```

## Security

WorldKeeper runs an HTTP server with **no authentication**. Anyone who can reach the port can view, create, download, and delete your backups. Treat it accordingly.

### Keep the port off the public internet

The single most important thing: **do not expose port 8081 (or whatever you configure) to the internet.** Your firewall should block it from external traffic. If you need remote access, use one of:

- **SSH tunnel**: `ssh -L 8081:localhost:8081 user@your-server`, then open `http://localhost:8081` locally
- **Reverse proxy with auth**: Put nginx/Caddy in front with HTTP basic auth or SSO

### IP whitelist

WorldKeeper supports an optional IP whitelist. **By default the list is empty, which means all IPs are allowed.** If you add IPs to the list, only those addresses can access the web UI — everything else gets a `403 Forbidden` and is logged as a warning.

To lock it down to localhost only:

```json
{
  "webServer": {
    "allowedIPs": ["127.0.0.1"]
  }
}
```

To allow specific IPs:

```json
{
  "webServer": {
    "allowedIPs": ["127.0.0.1", "192.168.50.10"]
  }
}
```

Note: The whitelist checks exact IP matches. It does not support CIDR ranges — list each IP individually, or use a reverse proxy for more complex rules.

### What's at risk if exposed

| Endpoint | Risk |
|----------|------|
| `POST /api/backups/create` | Disk exhaustion via backup spam |
| `DELETE /api/backups/delete/*` | All backups wiped |
| `GET /api/backups/download/*` | World data exfiltrated |
| `POST /api/backups/restore/*` | Restore triggered (if `allowRestore` is on) |

### Recommendations

- Keep `allowRestore` set to `false` unless you actively need it
- Monitor disk usage — even with retention limits, rapid manual backup creation can fill a disk before cleanup runs
- Check your server logs for "Blocked request from unauthorized IP" warnings — if you see them, something is probing the port

## Built with Claude Code

This mod was built using [Claude Code](https://claude.com/claude-code). We've included the artifacts from that process so you can see how it went and adapt it for your own projects:

- **[PLAN.md](PLAN.md)** — The full implementation plan that was written and approved before any code was generated. Some details changed during implementation (wrong Java version, wrong Jetty dependency names, undocumented plugin API quirks), but the architecture held.
- **[PROMPT.md](PROMPT.md)** — A reusable prompt you can give to an LLM to build something similar for your own game server, plus tips on what worked well and what to watch out for.

## Tech Stack

- Java 21
- Hytale Server API
- Embedded Jetty 12.1.4 (web server)
- Gson (JSON serialization)
- Maven with shade plugin (uber JAR)

## License

MIT
