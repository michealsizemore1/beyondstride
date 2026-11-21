# BeyondStride: rules for every Claude session

Read this before you change anything. These are the owner's standing decisions.

## The Life tab is permanent
- **Never remove, hide by default, or delete the Life tab** (LifeActivity, TodayLife, CcEngine, Life*.kt, assets/cc).
- "Disconnect the Command Center" or "remove the Command Center" means **stop the Life tab syncing with the Command Center website**. It does NOT mean removing the Life tab. The Life tab is the one real copy of that data now.
- If a request seems to mean removing the Life tab or its features, ask first. Do not guess.

## Command Center (CC) website
- Repo: michealsizemore1/ChatGPT-The-Master-App (daily-planner.html on GitHub Pages). **Keep the website up.** Never delete anything from it unless the owner asks.
- Since build-330 (Nov 21 2026) the app no longer syncs with the website in either direction, and the website no longer gets BeyondStride data. The website is frozen as it was.
- Keep `livetrack.html` (LiveTrack uses it).
- In My Life Master App, don't touch the other apps or shared files such as `master-backup-shared.js`.

## Phone and watch
- Phone and watch should match, **except** the Life tab and everything from the CC stays off the watch. Life notifications use `setLocalOnly(true)`.

## Keys and privacy
- There are two separate AI keys: the app's Anthropic key and the CC website's own `ai_key`. Keep them separate.
- The Anthropic key stays on the phone only. Never put it in backups, the cloud copy, the watch, the CC or Firebase. Life backups exclude `ai_key` and any secrets.
- The owner's email is private. Never send it to outside services.

## Data that must keep flowing
- Daily summaries still go to Firebase. Nutrition Monthly/Yearly and the built-in Life pages use them.
- The Life backup (life_backup.json in file backups) must keep working.
