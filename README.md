# Positively Geared — Android App
### by Aus Property Professionals

---

## BUILDING THE APK — Step by Step

### Prerequisites
1. Install **Android Studio** (free): https://developer.android.com/studio
2. During setup, install the **Android SDK** (Android Studio does this automatically)

---

### Steps to build your APK:

**Step 1** — Open Android Studio  
Click **"Open"** → select the `PositivelyGearedApp` folder

**Step 2** — Wait for Gradle sync  
Android Studio will automatically download all dependencies (~2 min first time)

**Step 3** — Build the APK  
Go to menu: **Build → Build Bundle(s) / APK(s) → Build APK(s)**

**Step 4** — Find your APK  
Click **"locate"** in the notification that appears, or find it at:  
`app/build/outputs/apk/debug/app-debug.apk`

**Step 5** — Distribute  
- Send via WhatsApp, email, or Google Drive to users  
- Users tap the file on their Android phone to install  
- They may need to enable "Install from unknown sources" in Settings

---

## HOW THE APP WORKS

1. **User installs the APK** on their Android phone
2. **App opens** → shows branded splash screen with Positively Geared logo
3. **Background download starts automatically** — all 11 MP3 chapters download silently
4. **Progress notification** shows in the status bar: "Downloaded 3/11 chapters..."
5. **Available chapters** show a green ✓ download icon — ready to play offline
6. **Resume banner** appears when returning to the app mid-chapter

## OFFLINE PLAYBACK
- Once a chapter is downloaded, it plays from local storage — no internet needed
- Files stored in: `/Android/data/com.app.positivelygeared/files/Music/PositivelyGeared/`
- Total storage needed: ~650MB for all 11 chapters

## APP FEATURES
- ✅ Branded splash screen (APP logo)  
- ✅ Auto-downloads all 11 MP3 chapters on install  
- ✅ Background download with progress notification  
- ✅ Offline playback — no internet after download  
- ✅ Resume where you left off  
- ✅ Speed control (1x, 1.25x, 1.5x, 1.75x, 2x)  
- ✅ Skip forward/back 30 seconds  
- ✅ Green download buttons with white arrow icon  
- ✅ White checkmark when chapter is saved offline  
- ✅ Intercepts MP3 requests to serve local files automatically  

---

## PUBLISHING TO GOOGLE PLAY (optional)
If you want the app on the Play Store:
1. Change `buildTypes.release.minifyEnabled` to `true` in `app/build.gradle`
2. Build → Generate Signed Bundle/APK → APK → create a keystore
3. Upload to https://play.google.com/console ($25 one-time fee)

---

*Built for Aus Property Professionals — auspropertyprofessionals.com.au*
