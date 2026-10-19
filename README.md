# AYESHI AI (v0.1.0 – Stages 1-3)

Gemini-powered Android assistant with a futuristic UI, voice in/out, app launcher and YouTube search.

## Kya kya kaam karta hai
- Gemini chat (REST API, apni API key; key Android Keystore se encrypted)
- Model list fetch, connection test, temperature, response style
- Speech-to-text (mic button), text-to-speech, voice select, speed, continuous mode
- Conversation history (search, delete, export), local memory (view/edit/delete)
- Hinglish/Hindi/English commands: "YouTube kholo", "Chrome open karo",
  "YouTube par Arijit Singh ke songs search karo", "yaad rakho: ..."
- Settings: AI, Voice, Personalization, Privacy, App integration, About

## Abhi NAHI hai (jaan-bujhkar fake button nahi diye)
Coding assistant screen, notes, reminders, web search, image/file assistant,
Termux command execution. Ye Stage 4-5 mein aayenge.

## Limitations
- YouTube par gaana apne aap play nahi hota; sirf search results khulte hain.
- Command router rule-based hai (short sentences). Gemini ka output kabhi command ki tarah execute nahi hota.
- Android voices ka gender detect nahi hota; "Test voice" se chuno.
- API key phone par rakhna personal use ke liye theek hai, production ke liye backend proxy chahiye.

## BUILD OPTION A (Recommended): GitHub Actions – Termux se push, cloud mein build
```
pkg update && pkg upgrade
pkg install git unzip
cd ~/storage/downloads   # (pehle: termux-setup-storage)
unzip AyeshiAI.zip && cd AyeshiAI
git init && git add . && git commit -m "AYESHI AI"
git branch -M main
git remote add origin https://github.com/<username>/<repo>.git
git push -u origin main      # password ki jagah GitHub Personal Access Token
```
Phir GitHub > Actions > "Build APK" > run complete hone par artifact
`ayeshi-ai-debug-apk` download karo, zip kholo, `app-debug.apk` install karo
(Unknown sources allow karna padega).

## BUILD OPTION B: Termux mein local build (experimental)
Official Android SDK build-tools x86 Linux ke liye hain; ARM Termux par aapt2 aksar fail hota hai.
```
pkg install openjdk-17 gradle unzip
```
1. Termux-compatible ARM64 Android SDK (platform android-34, build-tools) alag se install karo
   (community builds; main yahan koi URL guarantee nahi karta).
2. `local.properties` banao:  `sdk.dir=/path/to/android-sdk`
3. `gradle.properties` mein `android.aapt2FromMavenOverride=<ARM aapt2 ka path>` uncomment karo.
4. Gradle version 8.x hona chahiye (9.x AGP 8.5 ke saath nahi chalta): `gradle --version`
5. `gradle assembleDebug --no-daemon`
APK: `app/build/outputs/apk/debug/app-debug.apk`

Kam RAM (<6GB) par Gradle crash kar sakta hai.

## Pehli baar use
1. App kholo > Settings > Gemini API key paste > Save key
2. "Fetch models" se model chuno > "Test connection"
3. Home par chat karo ya mic dabao.

## Troubleshooting
- `SDK location not found` -> local.properties mein sdk.dir
- `aapt2 ... Syntax error`/exec format -> Option A use karo
- `Unsupported class file major version` -> JDK 17 use karo
- `API key invalid` -> key dobara paste karo
