package io.github.georgwav.cosmetics.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Molang subset model animations use. */
class MolangTest {
    private static double eval(String source, Molang.Context context) {
        return Molang.compile(source).eval(context);
    }

    private static double eval(String source) {
        return eval(source, new Molang.Context().clear());
    }

    @Test
    void arithmeticFollowsTheUsualOrder() {
        assertEquals(7, eval("1 + 2 * 3"), 1e-9);
        assertEquals(9, eval("(1 + 2) * 3"), 1e-9);
        assertEquals(-4, eval("-2 * 2"), 1e-9);
        assertEquals(0.5, eval("1 / 2"), 1e-9);
        assertEquals(0, eval("1 / 0"), 1e-9, "division by zero is 0, as in Bedrock");
        assertEquals(2.5, eval("2.5f"), 1e-9);
        assertEquals(0.001, eval("1e-3"), 1e-12);
    }

    @Test
    void mathTakesDegrees() {
        assertEquals(1, eval("math.sin(90)"), 1e-9);
        assertEquals(-1, eval("Math.Cos(180)"), 1e-9, "names are case-insensitive");
        assertEquals(45, eval("math.atan2(1, 1)"), 1e-9);
        assertEquals(3, eval("math.clamp(5, 0, 3)"), 1e-9);
        assertEquals(5, eval("math.lerp(0, 10, 0.5)"), 1e-9);
        assertEquals(Math.PI, eval("math.pi"), 1e-9);
        assertEquals(0, eval("math.no_such_function(3)"), 1e-9);
        assertTrue(Molang.compile("math.sin(30) * 2 + 1").isConstant(), "constants are worked out once");
    }

    @Test
    void queriesComeFromTheContext() {
        Molang.Context context = new Molang.Context().clear().set(Molang.Query.ANIM_TIME, 0.25);
        Molang.Expr flap = Molang.compile("math.sin(query.anim_time * 360) * 30");
        assertFalse(flap.isConstant());
        assertTrue(Molang.uses(flap, Molang.Query.ANIM_TIME));
        assertEquals(30, flap.eval(context), 1e-9);
        assertEquals(1, eval("q.is_on_ground", context), 1e-9);
        assertEquals(0, eval("q.unknown_query", context), 1e-9);
        context.set(Molang.Query.IS_ELYTRA_FLYING, true);
        assertEquals(1, eval("q.is_gliding", context), 1e-9);
    }

    @Test
    void logicAndConditions() {
        Molang.Context context = new Molang.Context().clear().set(Molang.Query.IS_SWIMMING, true);
        assertEquals(1, eval("q.is_swimming && !q.is_sneaking", context), 1e-9);
        assertEquals(0, eval("q.is_swimming && q.is_sneaking", context), 1e-9);
        assertEquals(7, eval("q.is_swimming ? 7 : 3", context), 1e-9);
        assertEquals(3, eval("q.is_sneaking ? 7 : 3", context), 1e-9);
        assertEquals(1, eval("2 >= 2 && 1 < 2 && 3 != 4 && 5 == 5"), 1e-9);
        assertEquals(0, eval("q.is_sneaking ? 7", context), 1e-9, "without ':', false is 0");
    }

    @Test
    void variablesAndReturn() {
        Molang.Context context = new Molang.Context().clear().set(Molang.Query.ANIM_TIME, 2);
        assertEquals(8, eval("v.speed = q.anim_time * 2; return v.speed * 2;", context), 1e-9);
        assertEquals(5, eval("temp.a = 2; t.b = 3; t.a + t.b"), 1e-9);
        assertEquals(0, eval("v.never_set"), 1e-9);
    }

    @Test
    void whatItCannotReadIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> Molang.compile("1 +"));
        assertThrows(IllegalArgumentException.class, () -> Molang.compile("(1"));
        assertThrows(IllegalArgumentException.class, () -> Molang.compile("loop(3, {v.x = 1;})"));
        assertThrows(IllegalArgumentException.class, () -> Molang.compile("1 2"));
    }
}
