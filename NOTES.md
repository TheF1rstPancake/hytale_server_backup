# Development Notes

Things we learned the hard way during development. Useful if you're picking this project back up or building another Hytale mod.

## Hytale Server API

There are **no official API docs** as of February 2026. Everything here was reverse-engineered.

### Where to find information

1. **Decompile the server JAR**: `javap -cp HytaleServer.jar -p <classname>` to get method signatures
2. **Read existing mod JARs**: The Nitrado mods in the `mods/` folder show real-world API usage. Decompile them with `javap` or a tool like CFR/Fernflower.
3. **Maven repos**:
   - Official: `https://maven.hytale.com/release`
   - Community: `https://maven.hytalemodding.dev/releases`

### Key API classes

| Class | Package | Notes |
|-------|---------|-------|
| `JavaPlugin` | `com.hypixel.hytale.server.core.plugin` | Base class for mods. Constructor **must** take `JavaPluginInit` parameter. |
| `PluginBase` | `com.hypixel.hytale.server.core.plugin` | Parent of JavaPlugin. Has `getDataDirectory()` returning `java.nio.file.Path`. |
| `JavaPluginInit` | `com.hypixel.hytale.server.core.plugin` | Passed to plugin constructor by the server. |
| `CommandManager` | `com.hypixel.hytale.server.core.command.system` | `CommandManager.get().handleCommand(sender, command)` to run server commands. |
| `ConsoleSender` | `com.hypixel.hytale.server.core.console` | `ConsoleSender.INSTANCE` — use as the sender for programmatic commands. |

### Plugin lifecycle

```
constructor(JavaPluginInit) → preLoad() → setup() → start() → shutdown()
```

- `preLoad()` returns `CompletableFuture<Void>` — use for config loading
- `setup()` — initialize components
- `start()` — start services (scheduler, web server)
- `shutdown()` — cleanup

### Manifest format

Place `manifest.json` at the JAR root (not in META-INF):

```json
{
  "Group": "com.example",
  "Name": "MyMod",
  "Version": "1.0.0",
  "Main": "com.example.MyPlugin",
  "ServerVersion": "*"
}
```

The server creates the mod's data directory at `mods/<Group>_<Name>/`.

### What to watch for

- Community docs may have **wrong package names**. Always verify against the actual JAR with `javap`.
- `getDataDirectory()` returns a **relative path**. Always call `.toAbsolutePath()` before calling `.toFile().getParentFile()` or you'll get null.
- The server bundles SLF4J and Gson on its classpath. Mark them as `provided` scope in Maven to avoid conflicts.

## Build Gotchas

### Gson + Java 21 module system

Gson uses reflection to serialize fields. On Java 21, if your class has a `java.io.File` or `Gson` field, serialization will fail with:

```
Failed making field 'java.io.File#path' accessible
```

Fix: mark those fields `transient` so Gson skips them.

### Maven shade plugin + Jetty

The shade plugin can corrupt Jetty's internal resources when merging JARs. This manifests as:

```
java.util.zip.ZipException: invalid LOC header (bad signature)
```

This happens when Jetty tries to serve error pages from resources inside the shaded JAR. Fix: set a minimal `ErrorHandler` on the Jetty server with `setShowStacks(false)` to prevent it from trying to load error page templates.

### Jetty 12 dependency names

Jetty 12 restructured its modules. The old `jetty-servlet` artifact doesn't exist. Use:
- `org.eclipse.jetty.ee10:jetty-ee10-servlet` (not `org.eclipse.jetty:jetty-servlet`)
- `org.eclipse.jetty:jetty-session`
- `org.eclipse.jetty:jetty-security`

The servlet API is in `jakarta.servlet`, not `javax.servlet`.
