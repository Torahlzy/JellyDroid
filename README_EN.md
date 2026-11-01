[简体中文](README.md) | **English**

# Jellydroid

**Make your Jellyfin library easier to browse, better to watch, and simpler to organize.**

Jellydroid is a third-party Jellyfin Android client built on [Findroid](https://github.com/jarnedemeulemeester/findroid), optimized for phones, tablets, and foldables. Beyond the native browsing and playback experience, it is **specifically tailored for Japanese movie (JAV) workflows**, integrating code-based metadata scraping, Chinese translation, cover management, and actor profiles into everyday viewing.

Connect to your own Jellyfin server and handle everything on mobile — from picking a movie to completing its metadata, playback, and offline downloads.

## Why Jellydroid

- **Find your next watch with direction**: enter the library through actors, genres, tags, years, or studios, or browse randomly to discover something new.
- **Metadata organizing made easy**: scrape movie info and covers by code, translate Japanese titles and summaries, then save to the server after confirmation.
- **Pick up where you left off**: multi-version selection, dual playback backends (ExoPlayer / mpv), plus background downloads with resume support.

## Tailored for Japanese Movies

Built around the habits of "organize by code, browse by actor, understand Japanese metadata", common tools are placed right in the movie detail page.

### Complete Movie Metadata by Code

Enter a code in "Edit nfo" to scrape title, overview, genres, release date, ratings, and more from your configured sites. Multiple sites can be scraped at once, with results switchable between sources — reducing manual searching and copy-pasting.

Title, original title, overview, genres, tags, studios, actors, and directors can also be edited manually. Scrape results only fill the form first; nothing is written to the server until you confirm and save.

### Turn Japanese Titles and Summaries into Chinese

Translate Japanese titles and summaries from scrape results or the form into Simplified Chinese, with an optional auto-translate after scraping, making the library easier to browse and understand.

Translation uses your own OpenAI-compatible API endpoint (DeepSeek and similar services work); you need to fill in the endpoint URL, API key, and model. The configuration is stored locally on your device.

### Pick the Right Cover for Each Movie

- **Scrape covers**: fetch landscape and portrait images from multiple sites by code, preview, then upload your pick.
- **View existing images**: browse all images of a movie on the server in one place, with fullscreen preview and pinch-to-zoom.
- **Use any frame as a cover**: capture the current frame during playback and upload it as a landscape cover.

### Discover More from Actors You Like

Tap an actor to see all their works in your library; browsing by director or writer is also supported. Long-press an actor entry to scrape candidate profile images by name, pick one, and upload it to make the people list more visual. You can also copy the full actor name for further searching or organizing.

Scrape site URLs can be configured in the movie detail page under "More → Scrape URLs"; once saved they are available on that page, and a separate proxy can be set for scraping. Release builds ship without any site URLs — you must configure them yourself. Unconfigured sites are skipped, and what you can actually fetch depends on each site's catalog and network conditions.

## A Library That's Better to Browse

- **Multiple entry points**: view works by genre, tag, rating, year, actor, director, writer, or studio; genres and tags inside detail pages are clickable too.
- **Switch covers freely**: toggle between portrait posters and landscape stills, combined with sorting and random browsing, to pick movies your way.
- **Favorites, your way**: the "Liked" page supports sorting and poster / still switching, sharing preferences with the library list to avoid redundant setup.
- **Smoother page transitions**: movie list covers blend seamlessly into detail page images, with text and action buttons fading in sequence; bottom tabs switch instantly, and the detail screen stays stable while holding the back gesture.
- **Comfortable on every screen size**: the home carousel adapts its layout to screen width for phones, tablets, and foldables; detail page background images are shown in full at their original aspect ratio.
- **Readable details**: long titles can expand or collapse, and the movie detail page shows both local and server file paths so you can confirm the source.
- **Collections at a glance**: collection cards carry a dedicated badge, and borrow an inner item's image when a cover is missing.
- **Search with less waiting**: previously searched keywords are cached, so switching back and forth is faster.

## A Smoother Playback Experience

- **Dual playback backends**: ExoPlayer and mpv, choose per your needs.
- **Multi-version selection**: when a movie has multiple files, pick a version before playback, with local and server files distinguished.
- **Common controls in one place**: subtitles, playback speed, and audio tracks are grouped in the playback menu; the transparent control bar keeps the picture visible.
- **Brightness, your habit**: brightness is only remembered after you adjust it manually; otherwise the system brightness applies.

## Download and Watch Offline

- **Background downloads**: downloads continue after you leave the download page, with progress shown in the notification.
- **Resume support**: interrupted downloads pick up where they stopped, cutting redundant waiting.
- **Concurrent multi-segment downloads**: enabled when the server supports it, falling back to a single connection automatically otherwise.
- **Clear status**: live speed and states like queued, paused, failed, and finished; downloaded items show their local file path.

## About the Project

Jellydroid is a personal fork of Findroid, currently focused on the Android phone app, with improved experiences for tablets and foldables.

- [Findroid upstream project](https://github.com/jarnedemeulemeester/findroid): the original project, screenshots, and community.
- [Release docs](wiki/release.md): version bumping, build signing, and GitHub release steps.
- [Pages & features index](wiki/pages.md): page implementations and feature entry points of this fork.

## License

This project is licensed under [GPLv3](LICENSE). Thanks to Findroid and related open-source projects. The original project name, logo, and related trademarks belong to their original authors and respective owners.
