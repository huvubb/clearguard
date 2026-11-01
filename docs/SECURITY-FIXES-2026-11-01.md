# Security fixes for the huvubb fork

Base: upstream V72 (`b0eed3d`). ClearGuard version: `1.0.0`.

- Keep native bridges inside the packaged `https://app.local/index.html` document. Block file/content access, mixed content, remote subresources and arbitrary intent navigation; open external HTTP(S) links in the browser.
- Disable the WebView bridge's raw shell command API. Existing callers receive an explanatory error. Native app management remains available.
- Restore manager-only permission administration and server-side pending confirmation checks. Remove automatic grants to third-party clients, including the path that overwrote explicit denials.
- Isolate pending requests by UID, PID, request code and permission. Expire old entries on registration, bound per-UID and global counts, and consume confirmations once under a lock.
- Bind saved grants to the installed package and signing certificate set. Legacy grants and changed identities require approval again. Adding or removing a package from a shared UID invalidates that UID's grants conservatively.
- Check user-service package ownership before reuse and deletion, and include the calling UID in cache keys. Capture the UID before clearing Binder identity.
- Remove sibling-app protection based only on package-name strings.

## Validation

Run the component regression checks with JDK 21 and a Gradle `lib` directory containing the Kotlin compiler:

```text
python tests/security_checks.py . GRADLE_LIB TEMP_OUTPUT_DIRECTORY
javac -encoding UTF-8 -d TEMP_JAVA_DIRECTORY app/src/main/java/com/youlong/hd/WebOriginPolicy.java tests/WebOriginPolicyCheck.java
java -ea -cp TEMP_JAVA_DIRECTORY WebOriginPolicyCheck
node tests/assets_check.js
```

The Kotlin checks compile the repository's actual `ConfigManager`, request registry and Shizuku adapter against minimal Android/storage stubs. They verify UID and certificate changes, stable-owner grants, cross-client request collisions, replay, concurrent consumption, quotas and service ownership. They do not replace Android device tests.

## ClearGuard distribution

ClearGuard includes a new complete, packaged five-page UI and original SVG/vector icon; no upstream private UI assets are required. It preserves the existing native capabilities and adds a private native terminal to retain interactive shell access without exposing raw commands to web content. All app names and app-label metadata are rebranded. Original copyright and license notices remain intact.

Wireless ADB activation validates pairing input, supports already-paired reconnect, records successful ports, and requires a ready Binder before reporting startup success. No unauthorized root exploit or permission bypass is used. The original native package ID is retained for protocol compatibility; the independent fork signature cannot update the official signer in place.

Browser regression checks cover page navigation, bridge routing, app search, malicious labels, diagnostics and appearance. Device-level ADB pairing, OEM behavior and long-running guardian operation require real-device follow-up.

No server credentials or signing material belong in this repository.
