package io.github.georgwav.cosmetics.devtools;

import io.github.georgwav.cosmetics.CosmeticsMod;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The smoke test's mixin audit: loads every class Sol's mixins go into, so each mixin is applied there and then, next to
 * every other installed mod's. A clash with another mod in a class the run would only reach later (a screen it never
 * opens) then fails the run instead of a player's game. Classes are loaded, not initialised, so nothing runs early.
 */
public final class MixinAudit {
    private static final String PACKAGE = "io/github/georgwav/cosmetics/mixin";
    private static final String MIXIN = Type.getDescriptor(org.spongepowered.asm.mixin.Mixin.class);

    private MixinAudit() {
    }

    /** Loads each target; a failure is added to {@code failures} and logged as an error. */
    public static void run(List<String> failures) {
        Set<String> targets;
        try {
            targets = targets();
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.error("Mixin audit: couldn't read Sol's mixins", e);
            failures.add("mixin audit: couldn't read Sol's mixins: " + e);
            return;
        }
        int loaded = 0, absent = 0;
        ClassLoader loader = MixinAudit.class.getClassLoader();
        for (String target : targets) {
            try {
                Class.forName(target, false, loader);
                loaded++;
            } catch (ClassNotFoundException e) {
                // A class of an optional mod that isn't installed.
                absent++;
            } catch (RuntimeException | Error e) {
                CosmeticsMod.LOGGER.error("Mixin audit: {} failed with Sol's mixins applied", target, e);
                failures.add("mixin audit: " + target + ": " + e);
            }
        }
        CosmeticsMod.LOGGER.info("Mixin audit: {} classes with Sol's mixins loaded ({} absent)", loaded, absent);
        if (loaded == 0) {
            failures.add("mixin audit: no target classes found");
        }
    }

    /** The classes Sol's mixins name in their {@code @Mixin} annotations, read from the jar (never loaded). */
    static Set<String> targets() throws IOException {
        Set<String> out = new TreeSet<>();
        for (Path root : FabricLoader.getInstance().getModContainer(CosmeticsMod.MOD_ID).orElseThrow().getRootPaths()) {
            Path dir = root.resolve(PACKAGE);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Path> classes;
            try (Stream<Path> files = Files.walk(dir)) {
                classes = files.filter(p -> p.toString().endsWith(".class")).toList();
            }
            for (Path file : classes) {
                ClassNode node = new ClassNode();
                new ClassReader(Files.readAllBytes(file)).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                List<AnnotationNode> annotations = new ArrayList<>();
                if (node.invisibleAnnotations != null) {
                    annotations.addAll(node.invisibleAnnotations);
                }
                if (node.visibleAnnotations != null) {
                    annotations.addAll(node.visibleAnnotations);
                }
                for (AnnotationNode a : annotations) {
                    if (MIXIN.equals(a.desc) && a.values != null) {
                        for (int i = 0; i + 1 < a.values.size(); i += 2) {
                            if (a.values.get(i + 1) instanceof List<?> list) {
                                for (Object v : list) {
                                    if (v instanceof Type type) {
                                        out.add(type.getClassName());
                                    } else if (v instanceof String name) {
                                        out.add(name.replace('/', '.'));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return out;
    }
}
