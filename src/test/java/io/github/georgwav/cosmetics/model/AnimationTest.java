package io.github.georgwav.cosmetics.model;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationTest {
    private static final float EPS = 1.0e-4f;

    private static CosmeticDefinition parse(String json) {
        return CosmeticParser.parse("test:thing", new StringReader(json));
    }

    @Test
    void cosmeticLevelAnimationAppliesToPartsWithoutTheirOwn() {
        CosmeticDefinition definition = parse("""
                {"slot": "head", "texture": "test:t.png",
                 "animation": [{"type": "spin", "speed": 0.5}, {"type": "bob", "amplitude": 2}],
                 "parts": [
                   {"cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}]},
                   {"animation": {"type": "swing", "axis": "z", "pivot": [1, 2, 3], "phase": 0.5},
                    "cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}]}
                 ]}
                """);
        var inherited = definition.parts().get(0).animations();
        assertEquals(2, inherited.size());
        assertEquals(CosmeticDefinition.AnimationType.SPIN, inherited.get(0).type());
        assertEquals(1, inherited.get(0).axis());
        assertEquals(0.5f, inherited.get(0).speed());
        assertEquals(2f, inherited.get(1).amplitude());

        var own = definition.parts().get(1).animations();
        assertEquals(1, own.size());
        CosmeticDefinition.Animation swing = own.get(0);
        assertEquals(CosmeticDefinition.AnimationType.SWING, swing.type());
        assertEquals(2, swing.axis());
        assertEquals(1f, swing.pivotX());
        assertEquals(3f, swing.pivotZ());
        assertEquals(15f, swing.amplitude()); // default swing
        assertEquals(0.5f, swing.phase());
    }

    @Test
    void partsMovingDifferentlyGetTheirOwnLayers() {
        BakedCosmetic cosmetic = CosmeticBaker.bake(parse("""
                {"slot": "back", "texture": "test:t.png",
                 "parts": [
                   {"animation": {"type": "swing", "amplitude": 20}, "cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}]},
                   {"animation": {"type": "swing", "amplitude": -20}, "cubes": [{"from": [2,0,0], "to": [3,1,1], "uv": [0,0]}]},
                   {"animation": {"type": "swing", "amplitude": 20}, "cubes": [{"from": [4,0,0], "to": [5,1,1], "uv": [0,0]}]},
                   {"cubes": [{"from": [6,0,0], "to": [7,1,1], "uv": [0,0]}]}
                 ]}
                """));
        // Equal animations share a layer (parts 1 and 3); the opposite wing and the still part don't.
        assertEquals(3, cosmetic.layers().length);
        assertEquals(12, cosmetic.layers()[0].mesh().quadCount());
        assertEquals(1, cosmetic.layers()[0].animations().length);
        assertEquals(0, cosmetic.layers()[2].animations().length);
        assertTrue(cosmetic.hasAnimation());
        assertFalse(cosmetic.hasEmissive());
    }

    @Test
    void valuesFollowTheCycle() {
        var spin = new CosmeticDefinition.Animation(CosmeticDefinition.AnimationType.SPIN, 1, 0, 0, 0, 0, 0.25f, 0);
        assertEquals(0f, spin.valueAt(0, 1f), EPS);
        assertEquals(90f, spin.valueAt(1, 1f), EPS);    // a quarter turn per second
        assertEquals(180f, spin.valueAt(1, 2f), EPS);   // twice the speed
        assertEquals(90f, spin.valueAt(5, 1f), EPS);    // wraps around after a full turn

        var swing = new CosmeticDefinition.Animation(CosmeticDefinition.AnimationType.SWING, 1, 0, 0, 0, 20, 0.5f, 0);
        assertEquals(0f, swing.valueAt(0, 1f), EPS);
        assertEquals(20f, swing.valueAt(0.5, 1f), EPS); // peak a quarter cycle in
        assertEquals(-20f, swing.valueAt(1.5, 1f), EPS);

        var outOfStep = new CosmeticDefinition.Animation(CosmeticDefinition.AnimationType.BOB, 1, 0, 0, 0, 1, 1f, 0.25f);
        assertEquals(1f, outOfStep.valueAt(0, 1f), EPS);
    }

    @Test
    void badAnimationsFailWithAClearMessage() {
        assertThrows(RuntimeException.class, () -> parse("""
                {"slot": "head", "texture": "test:t.png", "animation": {"type": "wobble"},
                 "parts": [{"cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}]}]}
                """));
        assertThrows(RuntimeException.class, () -> parse("""
                {"slot": "head", "texture": "test:t.png", "animation": {"type": "spin", "axis": "w"},
                 "parts": [{"cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}]}]}
                """));
        assertThrows(RuntimeException.class, () -> parse("""
                {"slot": "head", "texture": "test:t.png", "animation": "spin",
                 "parts": [{"cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}]}]}
                """));
    }
}
