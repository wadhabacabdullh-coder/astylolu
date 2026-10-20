# Jarvis 🎙

A voice AI assistant that runs on your Android phone. **OpenRouter** is the AI brain and **Fish Audio** is the voice. It's built to answer fast and spend very few credits.

## How it saves credits

- **Listening is free.** Chrome's built-in speech recognition turns your voice into text.
- **Free or cheap AI.** The default model is `openrouter/free`, which uses **zero credits**. It's rate-limited, though: 50 requests a day, or 1,000 a day once you've bought $10 of credits. For faster answers, tap **Cheapest** in ⚙ and pick a low-cost paid model. Many cost a few cents per million words.
- **Short replies.** Answers are 1–3 spoken sentences, capped at 200 tokens. Fish Audio charges by the amount of text, so short replies save voice credits too.
- **Short memory.** Only the last 8 messages are sent each turn.
- **No hidden reasoning.** "Thinking" models are told to keep reasoning brief and not send it back.
- **Spend counter.** The top-right corner shows OpenRouter's cost this session (`AI $…`) and how many characters Fish Audio has spoken (`🔊 …`).
- **Free voice backup.** If Fish Audio fails or runs out of credits, Jarvis switches to your phone's free voice. You can also choose **Phone voice (free)** in ⚙ at any time.

## How it responds fast

- The AI reply streams in word by word.
- The first sentence goes to Fish Audio on its own, so Jarvis starts talking almost immediately.
- Later sentences are fetched in the background while earlier ones play.
- Uses Fish Audio's low-latency "balanced" mode with small mp3 files.
- Paid models are routed to the fastest available provider.

## Why there's a small server

Fish Audio doesn't accept requests made directly from a web page. So Jarvis includes a tiny server (`server.js`, no extra packages needed) that runs **on your phone** in Termux. Chrome talks to it at `http://localhost:8080`, and it passes requests on to OpenRouter and Fish Audio. It only accepts connections from your own phone.

## Set it up on your phone (one time, about 5 minutes)

1. Install **Termux** from F-Droid (https://f-droid.org/packages/com.termux/). The Play Store version is outdated.
2. Open Termux and run:
   ```sh
   pkg update -y && pkg install -y nodejs git
   git clone -b ccr-8fe8b4f9-c6oqdd https://github.com/wadhabacabdullh-coder/astylolu.git
   cd astylolu/jarvis
   sh start.sh
   ```
3. Chrome opens `http://localhost:8080`. If it doesn't, open that address yourself.
4. Tap ⚙ and fill in:
   - **OpenRouter API key** (from https://openrouter.ai/keys)
   - **Fish Audio API key** (from https://fish.audio, under API Keys)
   - **Fish voice ID** (optional): open any voice on fish.audio and copy the long ID from its link
5. Tap **Save**, then tap the mic and talk. Allow microphone access when asked.
6. In Chrome, tap **⋮ → Add to Home screen** to get a Jarvis app icon.

## Every time after that

Open Termux and run:

```sh
sh astylolu/jarvis/start.sh
```

Then use the Jarvis icon. Termux must stay running in the background, so leave its notification there. For a one-tap start, install the **Termux:Widget** app and copy `start.sh` into `~/.shortcuts/`.

## Settings

| Setting | What it does |
|---|---|
| Model | Any OpenRouter model. **Cheapest** lists them by price; free ones are marked |
| Reply length | Short, medium, or long |
| Voice engine | Fish Audio, phone voice (free), or silent |
| Fish voice ID / model | Which Fish voice to use; `s2-pro` or `s1` |
| Hands-free | Jarvis starts listening again after each reply |
| Wake word | Jarvis only responds when you say "Jarvis …" |

**Tips:** tap the mic while Jarvis is talking to interrupt it. Tap ⟲ to clear the conversation.

## Keep your keys and credits safe

- Keys stay on your phone. They're saved in Chrome and sent only to the local server. You can also put them in `jarvis/.env` (see `.env.example`); that file is never committed.
- Set a **credit limit** on your OpenRouter key (https://openrouter.ai/keys) and keep an eye on your Fish Audio balance.
