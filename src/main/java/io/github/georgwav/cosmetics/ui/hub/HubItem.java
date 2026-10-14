package io.github.georgwav.cosmetics.ui.hub;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One usable orb of the hub: what it is called, its icon, and what using it does. */
final class HubItem {
    enum Kind {
        /** Opens another sphere. */
        LEVEL,
        /** Switches something on and off. */
        TOGGLE,
        /** Opens a page. */
        ACTION,
        /** Not built yet. */
        LATER
    }

    final String id;
    final String group;
    final Kind kind;
    /** Icon texture name under textures/gui/hub, or null. */
    String icon;
    /** The sphere a LEVEL opens. */
    String to;
    /** The island's button for an ACTION or LEVEL. */
    String button = "open";
    /** The player's own face (the wardrobe). */
    boolean you;
    /** Shows a live value instead of an icon: "fps" or "clock". */
    String live;
    BooleanSupplier on = () -> false;
    Consumer<Boolean> set = v -> {
    };
    Runnable action = () -> {
    };
    /** Why it can't be used right now (a mod that isn't installed), or null. */
    Supplier<String> missing = () -> null;
    /** Its name and note when they aren't the hub's own (a module's), or null. */
    Supplier<String> label;
    Supplier<String> note;
    /** The module this orb switches, whose settings its gear opens; null for other orbs. */
    String moduleId;

    HubItem(String id, String group, Kind kind) {
        this.id = id;
        this.group = group;
        this.kind = kind;
    }

    HubItem icon(String icon) {
        this.icon = icon;
        return this;
    }

    HubItem to(String level) {
        this.to = level;
        return this;
    }

    HubItem button(String button) {
        this.button = button;
        return this;
    }

    HubItem you() {
        this.you = true;
        return this;
    }

    HubItem live(String live) {
        this.live = live;
        return this;
    }

    HubItem toggle(BooleanSupplier on, Consumer<Boolean> set) {
        this.on = on;
        this.set = set;
        return this;
    }

    HubItem action(Runnable action) {
        this.action = action;
        return this;
    }

    HubItem missing(Supplier<String> missing) {
        this.missing = missing;
        return this;
    }

    /** An orb that switches one of the client's modules. */
    static HubItem module(io.github.georgwav.cosmetics.client.Module m, String group) {
        HubItem item = new HubItem(m.id, group, Kind.TOGGLE).icon(m.icon).toggle(m::enabled, m::setEnabled);
        item.label = m::name;
        item.note = m::note;
        item.moduleId = m.id;
        return item;
    }
}
