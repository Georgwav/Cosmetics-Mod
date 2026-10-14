package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.CosmeticsMod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Sunrise notifications: short messages that spring up from the bottom of the screen, with an optional action
 * (such as Undo), over the HUD and any screen; the last ones are kept in an inbox. Posting is thread safe; showing
 * them is the render thread's.
 */
public final class Notifications {
    /** How long one stays up, in seconds. */
    public static final float SECONDS = 4.5f;
    private static final int INBOX = 30;

    /** One message. {@code action} is the button's label, or null. */
    public static final class Note {
        public final String text;
        public final String action;
        public final Runnable onAction;
        public final long posted = System.nanoTime();
        /** Spring state, owned by the renderer. */
        public float shown;
        public float velocity;
        public boolean dismissed;

        Note(String text, String action, Runnable onAction) {
            this.text = text;
            this.action = action;
            this.onAction = onAction;
        }

        public float age() {
            return (System.nanoTime() - posted) / 1e9f;
        }
    }

    private static final Deque<Note> pending = new ArrayDeque<>();
    private static final List<Note> showing = new ArrayList<>();
    private static final Deque<Note> inbox = new ArrayDeque<>();

    private Notifications() {
    }

    public static void post(String text) {
        post(text, null, null);
    }

    public static void post(String text, String action, Runnable onAction) {
        Note note = new Note(text, action, onAction);
        synchronized (pending) {
            pending.add(note);
        }
        CosmeticsMod.LOGGER.info("[Sol] {}", text);
    }

    /** The notes on screen now (render thread): new ones join, old ones leave. */
    public static List<Note> showing() {
        synchronized (pending) {
            while (!pending.isEmpty()) {
                Note n = pending.poll();
                showing.add(n);
                inbox.addFirst(n);
                while (inbox.size() > INBOX) {
                    inbox.removeLast();
                }
            }
        }
        showing.removeIf(n -> n.dismissed || n.age() > SECONDS + 0.6f);
        return showing;
    }

    public static List<Note> inbox() {
        synchronized (pending) {
            return List.copyOf(inbox);
        }
    }
}
