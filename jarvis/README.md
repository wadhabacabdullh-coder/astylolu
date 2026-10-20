# Jarvis 🎙

A voice AI assistant for your Android phone, powered by Claude. It's built to answer fast and spend very little.

## How it saves credits

- **Voice is free.** Listening and speaking use your phone's built-in speech engine, not a paid API. Only the text goes to Claude.
- **Cheap, fast model by default.** It uses Claude Haiku 5.5 (about $0.10 per million input tokens and $0.50 per million output tokens), with thinking off and effort set to low.
- **Short replies.** Answers are kept to 1–3 spoken sentences, capped at 250 tokens. You can change this in ⚙.
- **Short memory.** Only the last 8 messages are sent each turn, so long chats don't get more expensive with every message.
- **Live spend counter.** The top-right corner shows what this session has cost. A typical question costs about $0.00005.

## How it responds fast

- Replies stream in word by word.
- Jarvis starts speaking as soon as the first sentence is finished, without waiting for the whole answer.
- The app is cached on your phone, so it opens instantly.

## Put it on your phone

1. **Host it (free) with GitHub Pages:** in this repository on GitHub, go to **Settings → Pages**. Under "Build and deployment", choose **Deploy from a branch**, pick the branch that holds the `jarvis/` folder, select `/ (root)`, and save.
2. After a minute, open this address in **Chrome on Android**:
   `https://wadhabacabdullh-coder.github.io/astylolu/jarvis/`
3. Tap **⋮ → Add to Home screen** (or **Install app**). Jarvis now opens like a normal app.
4. Open Jarvis, tap ⚙, paste your **Anthropic API key** (`sk-ant-…`), and tap Save.
5. Tap the mic and talk. Allow microphone access when Chrome asks.

Your API key is stored only in Chrome on your phone. It is never added to this repository, and it is only ever sent to `api.anthropic.com`.

## Settings

| Setting | What it does |
|---|---|
| Brain | Haiku 5.5 (cheapest and fastest), Sonnet 5.5, or Opus 5.5 (smartest, roughly 40× the cost of Haiku) |
| Reply length | Short, medium, or long |
| Voice / speed | Any voice installed on your phone |
| Hands-free | Jarvis starts listening again after each reply |
| Wake word | Jarvis only responds when you say "Jarvis …" |

**Tip:** tap the mic while Jarvis is talking to interrupt it. Tap ⟲ to clear the conversation.

## Protect your credits

In the [Anthropic Console](https://console.anthropic.com), set a **monthly spend limit** on your account. Use a separate API key just for Jarvis, so you can revoke it if your phone is lost.
