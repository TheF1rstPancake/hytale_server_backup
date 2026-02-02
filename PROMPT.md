# Build It Yourself

This mod was built with [Claude Code](https://claude.com/claude-code). Below is a prompt you can use to build something similar for your own game server — Hytale, Minecraft, or anything else that backs up a world folder.

You don't need to use this exact prompt. Adapt it to your server, your folder structure, and what you actually need. The point is to show that you can describe a real operational problem to an LLM and get working software out of it.

---

## The Prompt

```
I run a [GAME] dedicated server at [SERVER_PATH]. The server has a built-in backup
system that takes a backup every [X] minutes and keeps the last [Y] copies. Each
backup is about [SIZE] MB.

The problem: if something goes wrong and I don't notice for longer than [X * Y]
minutes, my oldest backup is already bad. I need both high-granularity recent
backups AND longer lookback periods.

Build me a backup mod/plugin that implements Grandfather-Father-Son (GFS) retention:

- "Snapshots" (frequent): Every [X] minutes, keep [N] backups
- "Dailies": One per day, keep [N] backups
- "Archives" (weekly): One per week, keep [N] backups

New backups always start as Snapshots. Promote the oldest Snapshot to Daily when
it's 24+ hours old. Promote the oldest Daily to Archive when it's 7+ days old.
Delete excess backups in each tier automatically.

The world data lives in [WORLD_FOLDER_PATH].
Backups should be ZIP files stored in [BACKUP_FOLDER_PATH].

I also want:
- A web dashboard (embedded HTTP server) to view/download/delete backups,
  organized by tier, with the active config visible so I know what's running
- Pre/post backup hooks (run server commands or shell scripts)
- JSON config file for all settings
- IP whitelist on the web server (default localhost only)
- The ability to restore from the web UI should be OFF by default and opt-in

The web UI should use the tier names Snapshots/Dailies/Archives (not the GFS
jargon) and each section header should show the interval and retention from the
active config so there's no ambiguity about what's happening.

Before you start coding:
1. Explore my server directory to understand the folder structure, existing
   mods/plugins, and how the current backup system works
2. Look at existing mods to understand the plugin API, lifecycle, and conventions
3. Write a plan and get my approval before implementing

Keep it simple. No unnecessary abstractions. No features I didn't ask for.
```

---

## Tips for Getting Good Results

**Let it explore first.** The best part of this process was pointing Claude at the server directory and letting it reverse-engineer the plugin API from existing mods and the server JAR. We didn't have official API docs — it used `javap` to decompile class signatures and figured out the right packages, constructor signatures, and lifecycle methods from what was already deployed.

**Ask for a plan.** We reviewed the full architecture before any code was written. This caught design decisions early (standalone mod vs. extending the existing backup system, Jetty vs. rolling our own HTTP server, time-based vs. count-based promotion).

**Deploy and iterate.** The first deploy hit a NullPointerException because the plugin API returned a relative path. The second hit a Gson reflection error on Java 21's module system. Both were fixed in minutes because the error was in server logs and Claude could read them directly. Don't expect it to work on the first try — expect it to recover fast.

**Push back on stubs.** The first version had `// TODO: integrate with server API` comments. We pushed back, pointed it at the actual server JAR, and got real API calls instead. If you see TODO markers, that's your cue to say "no, actually make it work."

**Name things for your audience.** We renamed from "GFS Backup Manager" to "WorldKeeper" and from "Grandfather/Father/Son" to "Archives/Dailies/Snapshots" because the people installing this mod don't care about backup theory — they want to know what the tiers mean at a glance.

---

## What You'll Need to Adapt

- **Plugin API**: This mod targets Hytale's `JavaPlugin` API. Minecraft servers use Bukkit/Spigot/Paper. Other games have their own plugin systems. The backup logic and web UI are the same regardless — the plugin wrapper is the only part that changes.
- **World folder**: Hytale uses `universe/`. Minecraft uses `world/`. Point the config at whatever your game uses.
- **Server commands**: The hook executor sends commands through `CommandManager.get().handleCommand()`. Replace this with your game's console command API.
- **Java version**: This targets Java 21. Adjust compiler settings in `pom.xml` if you're on a different version.
