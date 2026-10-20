# Jarvis for Android 🎙

A voice assistant app with a floating circle in the corner of your screen.

- **Opens apps:** "Open TikTok", "Open YouTube", and so on.
- **Controls TikTok:** "Scroll" (next video), "Like the post", "Comments", "Repost", "Share", "Close", "Swipe down", "Pause", "Auto scroll every 10 seconds".
- **Answers questions:** OpenRouter is the AI and Fish Audio is the voice.
- **The circle animates** while it listens (it grows with your voice), thinks (spinning) and talks (bouncing bars).

No Termux needed. The app talks to OpenRouter and Fish Audio directly.

## Install

1. On your phone, open the **latest release** of this repository: the **Releases** section on GitHub, "Jarvis for Android (latest)". Download **Jarvis.apk**.
2. Open the file and tap **Install**. If asked, allow Chrome (or Files) to install unknown apps.
3. Open **Jarvis** and go through the screen:
   1. **Allow microphone**
   2. **Allow showing over other apps**: find Jarvis and turn it on
   3. **Turn on phone control**: Accessibility → Installed apps → Jarvis phone control → On.
      If Android says **"Restricted setting"**: in Jarvis tap **Open Jarvis app info** → ⋮ (top right) → **Allow restricted settings**, then try again.
   4. Paste your **OpenRouter key**, choose a **model** (e.g. `google/gemini-3.5-flash`), and paste your **Fish Audio key** and voice ID.
4. Tap **Save & start Jarvis**. The circle appears in the bottom left.

## Using it

| Do this | What happens |
|---|---|
| Say **"Jarvis, …"** | Jarvis is always listening, no tapping needed (e.g. "Jarvis, open TikTok", "Jarvis, next") |
| Tap the circle | Talk without saying "Jarvis", or interrupt it while it talks. Tap while it's listening to pause it |
| Drag the circle | Move it anywhere (it remembers the spot) |
| Hold the circle | Opens the Jarvis settings screen |
| Notification → Stop | Turns Jarvis off |

**Phone commands** (instant and free, no AI credits): open *app*, scroll/next, swipe down/previous, like the post, comments, repost, share, close, pause/play, auto scroll (every N seconds), stop scrolling, go back, go home, recent apps, notifications, stop listening.

Comments, Share and Repost work by finding the button with that label on screen (Repost: Jarvis opens Share, then taps Repost).

Anything else is a question for the AI. Answers are short to save OpenRouter and Fish Audio credits.

## Notes

- **Always listening** is on by default and needs the wake word "Jarvis", so it won't answer TikTok sounds and waste credits. After Jarvis answers, you can reply once without saying "Jarvis".
- Android's speech recognizer may make a short beep or briefly lower other audio each time it restarts listening.
- Updates: a new APK is built whenever this folder changes. Install it over the old one; your settings are kept.
