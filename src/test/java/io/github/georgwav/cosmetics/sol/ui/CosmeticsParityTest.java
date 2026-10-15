package io.github.georgwav.cosmetics.sol.ui;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.config.CosmeticsConfig.CosmeticOptions;
import io.github.georgwav.cosmetics.model.CosmeticOption;
import io.github.georgwav.cosmetics.sol.ui.wardrobe.CosmeticControls;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing the Cosmetics screen could set is lost in the Wardrobe: every setting of the cosmetics config and of a
 * cosmetic's own settings is bound by a control (or has a place of its own, or is on the short list of old shapes
 * loading moves), each control really changes the setting it names, and every option a cosmetic file can switch off
 * decides whether some control shows.
 */
class CosmeticsParityTest {
    private static Set<String> settings(Class<?> type) {
        Set<String> out = new TreeSet<>();
        for (Field f : type.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isPublic(m) && !Modifier.isStatic(m) && !Modifier.isTransient(m)) {
                out.add(f.getName());
            }
        }
        return out;
    }

    private static Object read(Object o, String field) throws ReflectiveOperationException {
        return o.getClass().getField(field).get(o);
    }

    @Test
    void everyCosmeticsSettingHasAControl() {
        Set<String> bound = new HashSet<>();
        for (List<CosmeticControls.Control> page : CosmeticControls.GLOBAL.values()) {
            for (CosmeticControls.Control c : page) {
                if (c.field() != null) {
                    bound.add(c.field());
                }
            }
        }
        bound.addAll(CosmeticControls.ELSEWHERE.keySet());
        bound.addAll(CosmeticControls.NOT_CONTROLS.keySet());
        Set<String> missing = new TreeSet<>(settings(CosmeticsConfig.class));
        missing.removeAll(bound);
        assertEquals(Set.of(), missing, "settings with no control in the Wardrobe");
        Set<String> unknown = new TreeSet<>(bound);
        unknown.removeAll(settings(CosmeticsConfig.class));
        assertEquals(Set.of(), unknown, "controls naming no setting");
        assertEquals(4, CosmeticControls.NOT_CONTROLS.size(), "only the file's version and the three old shapes");
    }

    @Test
    void everyCosmeticsControlChangesTheSettingItNames() throws ReflectiveOperationException {
        for (String page : CosmeticControls.PAGES) {
            for (CosmeticControls.Control c : CosmeticControls.GLOBAL.get(page)) {
                CosmeticsConfig config = new CosmeticsConfig();
                switch (c) {
                    case CosmeticControls.Toggle t -> {
                        boolean before = t.get().test(config);
                        t.set().accept(config, !before);
                        assertEquals(!before, read(config, t.field()), t.key());
                        assertEquals(!before, t.get().test(config), t.key());
                    }
                    case CosmeticControls.Slider s -> {
                        double target = s.get().applyAsDouble(config) == s.max() ? s.min() : s.max();
                        s.set().accept(config, target);
                        assertEquals(target, ((Number) read(config, s.field())).doubleValue(), 1e-6, s.key());
                        assertEquals(target, s.get().applyAsDouble(config), 1e-6, s.key());
                    }
                    case CosmeticControls.Choice<?> ch -> choice(ch, config);
                    case CosmeticControls.Special sp -> assertFalse(sp.what().isEmpty());
                }
            }
        }
    }

    private static <T> void choice(CosmeticControls.Choice<T> ch, CosmeticsConfig config) throws ReflectiveOperationException {
        for (T value : ch.values()) {
            ch.set().accept(config, value);
            assertEquals(value, read(config, ch.field()), ch.key());
            assertEquals(value, ch.get().apply(config), ch.key());
        }
    }

    @Test
    void everyOwnSettingOfACosmeticHasAControl() {
        Set<String> bound = new HashSet<>();
        for (CosmeticControls.Own c : CosmeticControls.COSMETIC) {
            if (c.field() != null) {
                bound.add(c.field());
            }
        }
        Set<String> missing = new TreeSet<>(settings(CosmeticOptions.class));
        missing.removeAll(bound);
        assertEquals(Set.of(), missing, "a cosmetic's settings with no control on its page");
    }

    @Test
    void everyOwnControlChangesTheSettingItNames() throws ReflectiveOperationException {
        for (CosmeticControls.Own c : CosmeticControls.COSMETIC) {
            CosmeticOptions o = new CosmeticOptions();
            switch (c) {
                case CosmeticControls.OwnToggle t -> {
                    boolean before = t.get().test(o);
                    t.set().accept(o, !before);
                    assertEquals(!before, read(o, t.field()), t.key());
                }
                case CosmeticControls.OwnSlider s -> {
                    double before = s.get().applyAsDouble(o);
                    double target = before == s.max() ? s.min() : s.max();
                    s.set().accept(o, target);
                    assertEquals(target, s.get().applyAsDouble(o), 1e-6, s.key());
                    assertNotEquals(before, ((Number) read(o, s.field())).doubleValue(), s.key());
                }
                case CosmeticControls.OwnChoice<?> ch -> ownChoice(ch, o);
                case CosmeticControls.OwnSpecial sp -> assertFalse(sp.what().isEmpty());
            }
        }
    }

    private static <T> void ownChoice(CosmeticControls.OwnChoice<T> ch, CosmeticOptions o) throws ReflectiveOperationException {
        for (T value : ch.values()) {
            ch.set().accept(o, value);
            assertEquals(value, read(o, ch.field()), ch.key());
        }
    }

    @Test
    void everyOptionACosmeticCanSwitchOffHidesAControl() {
        List<CosmeticOption> gates = new ArrayList<>();
        for (CosmeticControls.Own c : CosmeticControls.COSMETIC) {
            if (c.gate().option() != null) {
                gates.add(c.gate().option());
            }
        }
        for (CosmeticOption option : CosmeticOption.values()) {
            assertTrue(gates.contains(option), option + " gates no control");
        }
    }
}
