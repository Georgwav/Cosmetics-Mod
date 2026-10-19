package io.github.georgwav.cosmetics.melee;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnifeBehaviourTest {
    private static final float CD = 0.625f, SWING = 0.3f;

    private static KnifeRig standIn() {
        KnifeGeo g = KnifeGeo.parse(StandInKnife.geo(), "standin");
        return new KnifeRig(g, KnifeClip.parseAll(StandInKnife.animations(), g));
    }

    /** Sounds played and stopped, by owner. */
    private static final class Log implements KnifeBehaviour.Sounds {
        final List<String> played = new ArrayList<>();
        final Set<Long> stopped = new HashSet<>();
        final List<Long> owners = new ArrayList<>();

        @Override
        public void play(String key, long owner) {
            played.add(key);
            owners.add(owner);
        }

        @Override
        public void stop(long owner) {
            stopped.add(owner);
        }
    }

    @Test
    void drawsAndInspectsSpreadOverTheirVariants() {
        KnifeRig rig = standIn();
        KnifeBehaviour b = new KnifeBehaviour(rig, new Random(1), new Log());
        Set<String> draws = new HashSet<>(), inspects = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            b.equip(i * 10f, CD, true, 0);
            b.update(i * 10f + 0.01f);
            draws.add(b.outMain.name);
            b.inspect(i * 10f + 1f);
            b.update(i * 10f + 1.01f);
            inspects.add(b.outMain.name);
        }
        assertEquals(Set.of("draw", "draw_1"), draws);
        assertEquals(Set.of("inspect", "inspect_1"), inspects);
    }

    @Test
    void lightAttacksAlternateAndCritsPlayTheHeavy() {
        KnifeBehaviour b = new KnifeBehaviour(standIn(), new Random(2), new Log());
        b.equip(0f, CD, true, 0);
        List<String> seen = new ArrayList<>();
        float now = 1f;
        for (int i = 0; i < 4; i++) {
            b.attack(now, false, true, CD, SWING, true);
            b.update(now + 0.01f);
            seen.add(b.outMain.name);
            now += 1f;
        }
        assertEquals(List.of("melee_1", "melee_2", "melee_1", "melee_2"), seen);
        b.attack(now, true, true, CD, SWING, true);
        b.update(now + 0.01f);
        assertEquals("melee_3", b.outMain.name);
        b.attack(now + 1f, true, false, CD, SWING, true);
        b.update(now + 1.01f);
        assertNotEquals("melee_3", b.outMain.name, "with heavy hits off a crit is a light attack");
    }

    @Test
    void anotherInspectStartsAFreshOneAtOnceAndAnAttackCancelsIt() {
        Log log = new Log();
        KnifeBehaviour b = new KnifeBehaviour(standIn(), new Random(3), log);
        b.equip(0f, CD, true, 0);
        b.update(2f);
        b.inspect(2f);
        b.update(2.2f);
        assertTrue(b.inspecting());
        b.inspect(2.3f);
        b.update(2.31f);
        assertTrue(b.inspecting());
        assertNull(b.outPrev, "an inspect restarts with no cross-fade");
        assertEquals(0.01f, b.outMainT, 1e-4f, "from its start");
        b.attack(2.5f, false, true, CD, SWING, true);
        b.update(2.51f);
        assertEquals(KnifeBehaviour.Kind.LIGHT, b.kind());
        b.inspect(5f);
        b.cancelInspect(5.2f);
        assertEquals(KnifeBehaviour.Kind.NONE, b.kind());
    }

    @Test
    void anAttacksSoundPlaysAndStopsWhenItIsCutOff() {
        Log log = new Log();
        KnifeBehaviour b = new KnifeBehaviour(standIn(), new Random(4), log);
        b.equip(0f, CD, true, 0);
        b.update(2f);
        b.attack(2f, false, true, CD, SWING, true);
        b.update(2.01f);
        assertEquals(List.of(StandInKnife.NAMESPACE + ":melee/" + StandInKnife.ID + "/swing"), log.played);
        long first = log.owners.get(0);
        b.attack(2.05f, false, true, CD, SWING, true);
        assertTrue(log.stopped.contains(first), "the cut-off attack's sound stops");
    }

    @Test
    void holdingAttackRepeatsOnlyOnceTheStrikeHasLanded() {
        KnifeBehaviour b = new KnifeBehaviour(standIn(), new Random(5), new Log());
        b.equip(0f, CD, true, 0);
        b.update(2f);
        b.swingRepeat(2f, CD, SWING, true);
        b.update(2.01f);
        String first = b.outMain.name;
        b.swingRepeat(2.02f, CD, SWING, true);
        b.update(2.03f);
        assertEquals(first, b.outMain.name, "mid-strike: no restart");
        b.swingRepeat(2.4f, CD, SWING, true);
        b.update(2.41f);
        assertNotEquals(first, b.outMain.name, "after the strike: the next light attack");
    }
}
