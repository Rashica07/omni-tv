# OMNI PERSONAL MEDIA LIBRARY: real end-to-end implementation

> **Status: QUEUED (saved 2026-10-25).** Not started on purpose. Start only after the current Gradle work has finished: the Cinema ⇄ launcher integration
> (contract v1, `docs/CINEMA-INTEGRATION.md`), the Server app test on the TV, and the Cinema / Weather / Server redesign.
> Overlap with finished work: media-library discovery and playback endpoints already exist in a first form (`omniptvd` `/status`, `/media` with Range,
> PIN auth; Cinema's library DB, scanner, TMDB scraper, Media3 player with resume). This brief extends them. It does not start a parallel server.

---

You are working on the OMNI TV ecosystem. Implement a complete, functional personal movie library that lets me place movies I legally own on my always-on Linux MacBook Pro, browse them through OMNI Cinema on my Android TV, and play them with a polished, premium streaming-service-style interface.

**This is an implementation task, not a mockup, redesign-only task, or architecture proposal. Inspect the existing repositories, understand the current code, implement the integration, build it, deploy it where possible, and test the real workflow.**

## 1. First, inspect the existing system

Before changing anything:

- Locate and inspect the OMNI Server codebase, OMNI Cinema Android app, OMNI launcher integration, existing APIs, authentication, network configuration, and current media-related functionality.
- Identify the actual Linux server environment, project paths, running services, ports, permissions, and deployment method.
- Inspect the Android TV's capabilities and supported video formats.
- Reuse existing working architecture wherever reasonable. Do not build a parallel server, duplicate services, replace working components unnecessarily, or rewrite the existing Java Views launcher.
- Determine what already works and what is missing. Do not assume existing features are implemented just because their UI exists.
- Preserve all existing OMNI functionality and the existing app's Kotlin + Jetpack Compose architecture.

If access to the server or TV is unavailable, implement everything possible in the available repository, clearly document what couldn't be tested, and provide exact deployment steps. Never claim to have deployed or tested something that you haven't.

## 2. The central requirement

I want this workflow to work:

1. My Linux MacBook Pro stays running at home with OMNI Server.
2. I place a movie file in a dedicated `OMNITVD/Movies/` directory.
3. OMNI Server discovers the movie and indexes it.
4. OMNI Cinema displays it with its title, poster, release year, description, genres, and other available metadata.
5. I open OMNI Cinema on my TV, select that movie, and watch it directly over my local network.
6. My playback position is saved, so I can resume watching later.
7. If I add more movies, they appear without manually editing a database or rebuilding the app.

Make this a real, repeatable workflow.

## 3. Create a proper media directory

Use the existing server's configuration and filesystem conventions. If there is no established media directory, create a configurable default such as:

`/srv/omni-tv/Movies/`

Also support a configurable alternative such as:

`~/OMNITVD/Movies/`

Do not silently assume these paths are interchangeable. Configure the actual path explicitly and display it in the server's settings.

Support a simple library structure such as:

```
OMNITVD/
- Movies/
  - Interstellar (2014)/
    - Interstellar (2014).mkv
    - poster.jpg (optional)
    - backdrop.jpg (optional)
    - movie.nfo (optional)
  - The Dark Knight (2008)/
    - The Dark Knight (2008).mp4
- TV Shows/ (optional future-ready library)
- Subtitles/ (optional, if a separate shared subtitle directory is supported)
```

Support common video formats and extensions, including MKV, MP4, M4V, AVI, MOV, and WebM where the existing playback pipeline can handle them.

Do not pretend every container and codec is compatible with the TV. Detect or report unsupported formats accurately.

The server must let me configure one or more library directories without hardcoding absolute paths throughout the application.

## 4. Implement a real media library scanner

Implement a persistent library index using the database or persistence technology already used by OMNI Server. If none exists, choose a lightweight solution appropriate for a small always-on Linux computer.

The scanner must:

- Perform an initial scan on setup.
- Discover new media files.
- Detect renamed, moved, modified, and deleted files.
- Support a manual rescan and automatic periodic rescanning.
- Avoid rescanning the entire library on every request.
- Use filesystem watching where reliable, with periodic scans as a fallback.
- Handle inaccessible directories, interrupted scans, malformed files, and server restarts without crashing.
- Avoid duplicate entries when scanning overlapping directories.
- Keep stable media IDs when practical.
- Store file paths, file size, modification time, duration when available, media type, codec information when available, metadata, artwork references, and scan status.
- Expose scan progress and useful errors in the server interface.
- Avoid reading entire video files into memory during scanning.

The scanner must never delete the actual movie files. Removing an entry from the library must not automatically erase the underlying media.

## 5. Automatic metadata and artwork

Integrate a legitimate movie metadata provider, such as TMDB, if appropriate for the project.

Requirements:

- Match titles using filenames and folder names.
- Parse release years to distinguish films with similar names.
- Allow manual correction when automatic matching is wrong.
- Retrieve available movie titles, descriptions, release years, genres, runtime, cast, directors, ratings where permitted, posters, and backdrops.
- Cache metadata and artwork locally to avoid repeated requests.
- Keep the library usable if the metadata service is unavailable.
- Support local poster and backdrop images as overrides.
- Provide a way to refresh metadata for a movie.
- Make metadata API keys configurable through server configuration or environment variables, never hardcode secrets or commit them.
- Respect provider terms, attribution requirements, image usage restrictions, and rate limits.
- Distinguish metadata availability from actual video availability.

If a movie cannot be matched, still display it using its filename, a clean fallback poster, and its actual playback availability. Do not hide valid files just because metadata lookup failed.

## 6. Build a real media API

Extend the existing OMNI Server API rather than creating a separate server without justification.

Provide versioned, authenticated endpoints equivalent to:

- `GET /api/v1/library`
- `GET /api/v1/movies`
- `GET /api/v1/movies/{id}`
- `GET /api/v1/movies/{id}/stream`
- `GET /api/v1/movies/{id}/subtitles`
- `GET /api/v1/library/scan`
- `POST /api/v1/library/scan`
- `GET /api/v1/progress`
- `PUT /api/v1/progress/{id}`
- `GET /api/v1/health`

Adapt endpoint names to the existing conventions if needed, but document the final contract.

The API must support:

- Pagination and search.
- Filtering and sorting.
- Continue Watching.
- Recently Added.
- Genre-based browsing.
- Movie detail retrieval.
- Artwork retrieval or safe artwork URLs.
- Scan status and server health.
- Playback progress updates.
- Subtitle discovery and selection where supported.
- Clear errors for unavailable files, unsupported playback, missing metadata, authentication failures, and server errors.

Protect all endpoints that expose private library information or media files. Use the existing authentication system where possible. Otherwise implement suitable local-network authentication and secure device pairing.

Do not expose arbitrary filesystem paths as playable URLs. Do not permit path traversal, unauthorised file access, or directory listing outside configured libraries.

## 7. Streaming and playback must actually work

This is one of the highest-priority requirements.

Implement real video streaming from OMNI Server to OMNI Cinema.

- Stream the original media file directly when compatible with the TV.
- Support HTTP byte-range requests, including correct `206 Partial Content`, `Content-Range`, and `Accept-Ranges` behaviour where applicable.
- Support seeking without downloading the entire movie first.
- Set appropriate content types and handle invalid ranges and interrupted connections.
- Do not load entire video files into application memory.
- Use efficient buffered file access and streaming.
- Keep network and filesystem operations off the Android main thread.
- Do not transcode by default. The server is an older MacBook Pro with limited resources.
- If transcoding is required for a specific unsupported format, detect that case and report it clearly. Implement optional transcoding only if it is practical, properly configured, and tested on the real server.
- Never silently claim a file is playable when it isn't.
- Handle server disconnections, missing files, network errors, and playback failures gracefully.

On Android TV, use an appropriate native playback engine such as Media3 ExoPlayer if that fits the existing project.

Implement:

- Play, pause, seek, and resume.
- Remote-control navigation.
- Playback progress persistence.
- Automatic resume prompts for partially watched movies.
- A sensible completion threshold so finished films disappear from Continue Watching.
- Back-button behaviour that returns to the movie details or library correctly.
- Subtitles when supported, including compatible external subtitle files.
- Audio and subtitle track selection when available.
- Loading, buffering, playback error, and retry states.
- Correct focus handling and focus restoration after playback.

Use actual playback. Do not use a fake player screen or sample video to simulate completion.

## 8. Build the premium OMNI Cinema interface

The visual quality should be comparable in polish to a modern commercial streaming application, while retaining the OMNI identity.

This must be a native Kotlin + Jetpack Compose Android TV interface, not a WebView, HTML page, website template, or desktop UI squeezed onto a TV.

Design direction:

- Deep charcoal and black backgrounds.
- High-quality cinematic poster and backdrop artwork.
- Restrained glass-like surfaces where appropriate.
- Strong typography and clear visual hierarchy.
- Large, beautiful posters and landscape artwork.
- Subtle motion and transitions.
- Distinct, highly visible remote-control focus states.
- Generous TV-safe spacing.
- Fast scrolling and predictable D-pad navigation.
- No excessive gradients, generic dashboard cards, or pointless decorative widgets.

Build the following functional screens.

### Cinema Home

- Cinematic featured movie with backdrop, title, description, and real available actions.
- Continue Watching.
- Recently Added.
- All Movies.
- Genres.
- A searchable library.
- A clear empty-library experience with setup instructions.
- A visible connection state when OMNI Server is unavailable.

### Movie Details

- Backdrop and poster.
- Title and release year.
- Runtime and genres when known.
- Description.
- Cast and director when available.
- Play or Resume Watching.
- Watch Again when appropriate.
- Metadata refresh or correction where appropriate.
- Honest playback availability and unsupported-format messaging.

### Library and Search

- Browse every indexed movie, including movies without metadata.
- Search titles.
- Sort by title, year, recently added, and other useful supported fields.
- Filter by genre where metadata exists.
- Provide useful loading, error, empty, and offline states.

Every button must do what it says. Do not populate the library with hardcoded demo titles, fake statistics, invented playback states, or placeholder actions.

## 9. OMNI launcher integration

Integrate the real library with the existing OMNI launcher and its Watch experience.

- Reuse the existing launcher-to-Cinema integration where possible.
- Add a real Continue Watching shelf and useful movie discovery shelves where the launcher architecture supports them.
- Use stable media IDs and a documented deep-link or intent contract to open a specific movie in Cinema.
- Opening a movie from the launcher should lead to its real details or playback screen.
- Keep focus restoration and Back navigation correct.
- Handle Cinema not being installed, the server being offline, and the library being empty.
- Do not break the launcher or make it dependent on a running media server for normal navigation.
- Avoid duplicating entire movie metadata databases across applications unnecessarily.
- Keep the server connection configurable so its address can change without rebuilding the APK.

If the launcher has no appropriate shelf system yet, make the smallest integration necessary rather than redesigning the entire launcher.

## 10. Server reliability and resource usage

The server runs on an older Linux MacBook Pro that may remain on continuously.

- Run OMNI Server as a manageable system service using the existing deployment mechanism or systemd where appropriate.
- Configure restart-on-failure without creating endless restart loops.
- Ensure library scanning and metadata lookups cannot block playback requests.
- Avoid unnecessary Docker containers, background workers, database servers, and heavyweight dependencies.
- Avoid transcoding unless necessary.
- Cache artwork and metadata responsibly.
- Prevent repeated network polling and redundant requests.
- Provide clear logs and useful health information.
- Support clean shutdown and recovery after a reboot.
- Document how to start, stop, update, and diagnose the service.
- Keep the media directory persistent across application updates.
- Do not weaken Linux filesystem permissions to make the application work.

The server should remain useful even when the internet is unavailable. Local playback, locally cached metadata, and locally stored artwork should continue to work wherever possible.

## 11. Security and privacy

This is my private home media library.

- Keep the service local-network-only by default.
- Do not expose it to the public internet or automatically configure router port forwarding.
- Authenticate devices and protect playback-progress data.
- Validate every requested media ID and resolved filesystem path.
- Prevent directory traversal, arbitrary file reads, and command injection.
- Store secrets outside source control.
- Avoid logging credentials or sensitive tokens.
- Use HTTPS where appropriate for the deployment, especially outside a trusted local network.
- Document network and firewall requirements.
- Keep third-party metadata access separate from private media access.

The implementation must not bypass DRM, paywalls, or streaming-service access controls. The source of playback for this feature is my own local media library.

## 12. Testing and acceptance criteria

Do not stop when the code compiles.

Create automated tests for the scanner, metadata matching, persistence, authentication, range requests, path security, progress tracking, and API behaviour.

Use a small set of test media files for development, not a permanently hardcoded demonstration library.

Then test this exact end-to-end workflow:

1. Start OMNI Server.
2. Create the configured Movies directory.
3. Copy a valid test movie into it.
4. Trigger or wait for a library scan.
5. Confirm the movie appears in the server's indexed library.
6. Confirm metadata and artwork load when available.
7. Open OMNI Cinema on the actual Android TV.
8. Confirm the movie appears in Cinema without rebuilding the APK.
9. Start playback.
10. Seek to another position.
11. Exit playback.
12. Reopen the movie and verify resume behaviour.
13. Add another movie and verify that it appears.
14. Restart the server and verify that the library and playback progress persist.
15. Disconnect the server and verify that the TV displays a useful offline state instead of freezing.
16. Test an unsupported or malformed media file and confirm that the application reports the problem honestly.

Verify byte-range requests and seeking with real files. Capture screenshots of the actual TV interface. Record build and deployment results, and report any tests that could not be completed.

## 13. Execution order

Work in this order:

1. Inspect the existing architecture and document findings.
2. Establish the real media directory and configuration.
3. Implement the scanner and persistent library index.
4. Implement metadata and artwork caching.
5. Implement and test the media API and secure streaming.
6. Connect OMNI Cinema to the actual API.
7. Implement native video playback and progress tracking.
8. Build the premium Cinema interface.
9. Integrate the library into the OMNI launcher.
10. Run automated tests, deploy to the real server and TV where possible, and fix discovered problems.

Do not start by redesigning the UI. Make the data and playback pipeline work first, then build the interface around the real data.

Do not stop after producing a plan. Continue implementing until the available environment, permissions, or missing access genuinely prevent further progress.

## 14. Final report

When finished, report:

- What was already implemented and what you added.
- The exact media directory configured on the server.
- How I add a movie.
- The server address and port actually configured, without inventing values.
- How Cinema connects to the server.
- The playback formats verified on the TV.
- Whether seeking, subtitles, and resume were tested.
- The actual tests passed and failed.
- The actual memory and CPU usage observed during playback, if measured.
- The files changed and commands needed to deploy or update the apps.
- Anything still incomplete, with a clear explanation.

**Definition of done:** I can copy a movie I own into the configured folder on my Linux MacBook Pro, open OMNI Cinema on my TV, see the movie in the real library with appropriate artwork, select it, watch it over my home network, and resume playback later. The whole workflow must function without hardcoded demo content or manual database editing.

---
## STATUS (2026-10-25)
Server half DONE and committed in omniptv: scanner, header probe, TMDB cache, `/api/v1`, tests, installed as the system service (media root `/srv/omni-media/Movies`).
Cinema now reads that library (`ServerApi`, sync from server, watch position push/pull) and plays through `/api/v1/movies/{id}/stream`; verified on the TV (poster/overview/backdrop from the server, 90 s of playback arrived at the server as progress).
Still open: manual re-match UI, scan-now button on the TV, Shows from the server library, subtitle choice UI, launcher shelf using server progress directly.
