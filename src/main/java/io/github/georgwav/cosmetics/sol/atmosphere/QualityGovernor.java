package io.github.georgwav.cosmetics.sol.atmosphere;

/**
 * Picks the particle budget on "auto" quality from how long frames take: one step down after two seconds over
 * the target, one step up after five seconds with room to spare.
 */
public final class QualityGovernor {
    private static final int[] BUDGETS = {300, 600, 1200};
    private static final float SLOW = 1f / 45f;
    private static final float FAST = 1f / 90f;
    private static int level = 1;
    private static float average = 1f / 60f;
    private static float slowFor;
    private static float fastFor;

    private QualityGovernor() {
    }

    /** Called once per frame with the frame's length in seconds. */
    public static void frame(float seconds) {
        if (seconds <= 0f || seconds > 0.5f) {
            return;
        }
        average += (seconds - average) * 0.05f;
        if (average > SLOW) {
            slowFor += seconds;
            fastFor = 0f;
            if (slowFor > 2f && level > 0) {
                level--;
                slowFor = 0f;
            }
        } else if (average < FAST) {
            fastFor += seconds;
            slowFor = 0f;
            if (fastFor > 5f && level < BUDGETS.length - 1) {
                level++;
                fastFor = 0f;
            }
        } else {
            slowFor = 0f;
            fastFor = 0f;
        }
    }

    public static int budget() {
        return BUDGETS[level];
    }

    /** 0 (low) to 2 (high). */
    public static int level() {
        return level;
    }
}
