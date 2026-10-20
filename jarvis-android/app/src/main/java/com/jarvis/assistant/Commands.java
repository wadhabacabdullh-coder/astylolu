package com.jarvis.assistant;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns short spoken phrases into phone actions. These run instantly on the
 * phone and never touch the AI, so they cost nothing.
 */
final class Commands {

    enum Type {
        OPEN_APP, SWIPE_UP, SWIPE_DOWN, BACK, HOME, RECENTS, NOTIFICATIONS,
        PLAY_PAUSE, LIKE, AUTO_SCROLL, STOP_SCROLL, STOP_LISTENING
    }

    static final class Action {
        final Type type;
        final String arg;   // app name for OPEN_APP
        final int seconds;  // interval for AUTO_SCROLL

        Action(Type type, String arg, int seconds) {
            this.type = type;
            this.arg = arg;
            this.seconds = seconds;
        }

        @Override
        public String toString() {
            return type + (arg != null ? "(" + arg + ")" : "") + (seconds > 0 ? "/" + seconds + "s" : "");
        }
    }

    // Longer sentences are questions for the AI, not commands.
    private static final int MAX_COMMAND_WORDS = 8;

    private static final Pattern FILLER = Pattern.compile(
            "^(?:(?:hey|ok|okay|yo)\\s+)?(?:jarvis\\s*)?(?:(?:please|can you|could you|would you|will you|i want to|i wanna|let's|lets)\\s+)*");
    private static final Pattern OPEN = Pattern.compile("^(?:open|launch|start|run|go to|switch to)\\s+(?:up\\s+)?(.+)$");
    private static final Pattern AUTO = Pattern.compile(
            "^(?:start\\s+)?(?:auto\\s*scroll(?:ing)?|keep (?:scrolling|swiping))(?:\\s+every\\s+(\\d+)\\s*(?:seconds?|secs?|s))?$");

    private Commands() {}

    static Action parse(String raw) {
        String t = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9' ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        t = FILLER.matcher(t).replaceFirst("").trim();
        t = t.replaceAll("\\s+(?:please|for me|now|thanks|thank you)$", "").trim();
        if (t.isEmpty() || t.split(" ").length > MAX_COMMAND_WORDS) return null;

        Matcher m = AUTO.matcher(t);
        if (m.matches()) {
            int s = m.group(1) != null ? Integer.parseInt(m.group(1)) : 15;
            return new Action(Type.AUTO_SCROLL, null, Math.max(3, Math.min(s, 600)));
        }
        if (t.matches("(?:stop|end|cancel) (?:the )?(?:auto ?)?(?:scroll(?:ing)?|swip(?:e|ing))")) return act(Type.STOP_SCROLL);
        if (t.matches("(?:stop listening|go to sleep|goodbye|good bye|bye|that's all|dismiss)")) return act(Type.STOP_LISTENING);

        if (t.matches("(?:swipe|scroll) up|next(?: video| one| tiktok| reel| short)?|skip(?: it| this| video| this video)?|scroll down|swipe")) {
            return act(Type.SWIPE_UP);
        }
        if (t.matches("(?:swipe|scroll) down|previous(?: video| one)?|last (?:video|one)|go back (?:a|one) video|back (?:a|one) video|scroll up")) {
            return act(Type.SWIPE_DOWN);
        }
        if (t.matches("(?:go )?back")) return act(Type.BACK);
        if (t.matches("(?:go )?home(?: screen)?|go to (?:the )?home(?: screen)?|close (?:the )?app|minimi[sz]e")) return act(Type.HOME);
        if (t.matches("(?:show |open )?(?:the )?recent(?:s| apps)")) return act(Type.RECENTS);
        if (t.matches("(?:show |open |pull down )?(?:the |my )?notifications")) return act(Type.NOTIFICATIONS);
        if (t.matches("pause(?: it| this| video| the video)?|play(?: it| video)?|resume|unpause|stop (?:the )?video")) return act(Type.PLAY_PAUSE);
        if (t.matches("(?:like|heart|love)(?: it| this| that| (?:this |the )?video)?|double tap")) return act(Type.LIKE);

        m = OPEN.matcher(t);
        if (m.matches()) {
            String app = m.group(1).replaceAll("^(?:the|my)\\s+", "").replaceAll("\\s+app$", "").trim();
            if (!app.isEmpty()) return new Action(Type.OPEN_APP, app, 0);
        }
        return null;
    }

    private static Action act(Type t) {
        return new Action(t, null, 0);
    }
}
