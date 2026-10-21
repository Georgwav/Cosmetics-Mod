package io.github.georgwav.cosmetics.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The part of Molang (Bedrock's expression language) that model animations use: numbers, arithmetic, comparisons,
 * {@code && || !}, {@code ?:} and {@code ??}, {@code math.*} functions (angles in degrees, as in Bedrock), queries
 * ({@code q.anim_time}, {@code query.is_swimming}, ... see {@link Query}) and variables ({@code v.x = ...; return
 * v.x * 2;}). Names are case-insensitive. Queries and functions it doesn't know are 0, as in Bedrock.
 *
 * <p>Expressions are compiled once at load into a small tree; evaluating one allocates nothing. Constant parts are
 * worked out at compile time.
 *
 * <p>Pure Java with no Minecraft types, so it is unit-tested without a game instance.
 */
public final class Molang {
    private Molang() {
    }

    /** The queries the mod answers; every other {@code query.*} is 0. */
    public enum Query {
        /** Seconds since the animation started playing. */
        ANIM_TIME("anim_time"),
        /** Seconds since the cosmetic started being drawn (the game's clock here). */
        LIFE_TIME("life_time"),
        /** Drawn in a screen (the Wardrobe preview and its tiles), not in the world. */
        IS_IN_GUI("is_in_gui"),
        /** The local player's own arms in first person. */
        IS_FIRST_PERSON("is_first_person"),
        /** Gliding with an elytra; also answers {@code is_gliding}. */
        IS_ELYTRA_FLYING("is_elytra_flying"),
        IS_SWIMMING("is_swimming"),
        IS_SNEAKING("is_sneaking"),
        /** Flying in creative or spectator. */
        IS_FLYING("is_flying"),
        IS_BLOCKING("is_blocking"),
        IS_MOVING("is_moving"),
        IS_ON_GROUND("is_on_ground"),
        IS_SPRINTING("is_sprinting"),
        IS_RIDING("is_riding"),
        /** Horizontal speed, blocks per second. */
        GROUND_SPEED("ground_speed"),
        /** Distance walked, in blocks (for walk cycles). */
        MODIFIED_DISTANCE_MOVED("modified_distance_moved"),
        /** A pet that follows you, sitting (or hovering low) while you stand still; else the wearer riding. */
        IS_SITTING("is_sitting");

        private final String id;

        Query(String id) {
            this.id = id;
        }

        static Query byName(String name) {
            if (name.equals("is_gliding")) {
                return IS_ELYTRA_FLYING;
            }
            if (name.equals("walk_distance") || name.equals("distance_moved")) {
                return MODIFIED_DISTANCE_MOVED;
            }
            for (Query q : values()) {
                if (q.id.equals(name)) {
                    return q;
                }
            }
            return null;
        }
    }

    /** What expressions read: the queries' current values. Reused; set before evaluating. */
    public static final class Context {
        private final double[] values = new double[Query.values().length];

        public Context set(Query query, double value) {
            values[query.ordinal()] = value;
            return this;
        }

        public Context set(Query query, boolean value) {
            return set(query, value ? 1.0 : 0.0);
        }

        public double get(Query query) {
            return values[query.ordinal()];
        }

        /** Every query back to 0, except standing on the ground. */
        public Context clear() {
            java.util.Arrays.fill(values, 0.0);
            values[Query.IS_ON_GROUND.ordinal()] = 1.0;
            return this;
        }
    }

    /** A compiled expression. */
    public abstract static class Expr {
        public abstract double eval(Context context);

        /** Always the same value, whatever the context. */
        public boolean isConstant() {
            return false;
        }

        /** Reads {@code q.anim_time}, so its value changes as the animation plays. */
        boolean usesQuery(Query query) {
            return false;
        }
    }

    /** A plain number. */
    public static Expr constant(double value) {
        return new Constant(value);
    }

    /**
     * Compiles {@code source}. Throws {@link IllegalArgumentException} with the reason when it isn't Molang this
     * class understands (a syntax error, a loop).
     */
    public static Expr compile(String source) {
        return new Parser(source).program();
    }

    /** Whether {@code expr} reads {@code query}. */
    public static boolean uses(Expr expr, Query query) {
        return expr.usesQuery(query);
    }

    // ------------------------------------------------------------------------------------------------ nodes

    private static final class Constant extends Expr {
        final double value;

        Constant(double value) {
            this.value = value;
        }

        @Override
        public double eval(Context context) {
            return value;
        }

        @Override
        public boolean isConstant() {
            return true;
        }
    }

    private static final class QueryNode extends Expr {
        final Query query;

        QueryNode(Query query) {
            this.query = query;
        }

        @Override
        public double eval(Context context) {
            return context.values[query.ordinal()];
        }

        @Override
        boolean usesQuery(Query q) {
            return q == query;
        }
    }

    /** One compiled program's variables, shared by its nodes. */
    private static final class Slots {
        double[] values = new double[0];
    }

    /** A variable of one compiled program. */
    private static final class Variable extends Expr {
        final Slots slots;
        final int index;

        Variable(Slots slots, int index) {
            this.slots = slots;
            this.index = index;
        }

        @Override
        public double eval(Context context) {
            return slots.values[index];
        }
    }

    private enum Op { ADD, SUB, MUL, DIV, LT, LE, GT, GE, EQ, NE, AND, OR, COALESCE }

    private static final class Binary extends Expr {
        final Op op;
        final Expr a;
        final Expr b;

        Binary(Op op, Expr a, Expr b) {
            this.op = op;
            this.a = a;
            this.b = b;
        }

        @Override
        public double eval(Context c) {
            double x = a.eval(c);
            switch (op) {
                case AND:
                    return x != 0 && b.eval(c) != 0 ? 1 : 0;
                case OR:
                    return x != 0 || b.eval(c) != 0 ? 1 : 0;
                case COALESCE:
                    return x;
                default:
                    break;
            }
            double y = b.eval(c);
            return switch (op) {
                case ADD -> x + y;
                case SUB -> x - y;
                case MUL -> x * y;
                case DIV -> y == 0 ? 0 : x / y;
                case LT -> x < y ? 1 : 0;
                case LE -> x <= y ? 1 : 0;
                case GT -> x > y ? 1 : 0;
                case GE -> x >= y ? 1 : 0;
                case EQ -> x == y ? 1 : 0;
                case NE -> x != y ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        boolean usesQuery(Query q) {
            return a.usesQuery(q) || b.usesQuery(q);
        }
    }

    private static final class Not extends Expr {
        final Expr a;

        Not(Expr a) {
            this.a = a;
        }

        @Override
        public double eval(Context c) {
            return a.eval(c) == 0 ? 1 : 0;
        }

        @Override
        boolean usesQuery(Query q) {
            return a.usesQuery(q);
        }
    }

    private static final class Negate extends Expr {
        final Expr a;

        Negate(Expr a) {
            this.a = a;
        }

        @Override
        public double eval(Context c) {
            return -a.eval(c);
        }

        @Override
        boolean usesQuery(Query q) {
            return a.usesQuery(q);
        }
    }

    private static final class Conditional extends Expr {
        final Expr test;
        final Expr then;
        final Expr otherwise;

        Conditional(Expr test, Expr then, Expr otherwise) {
            this.test = test;
            this.then = then;
            this.otherwise = otherwise;
        }

        @Override
        public double eval(Context c) {
            return test.eval(c) != 0 ? then.eval(c) : otherwise.eval(c);
        }

        @Override
        boolean usesQuery(Query q) {
            return test.usesQuery(q) || then.usesQuery(q) || otherwise.usesQuery(q);
        }
    }

    private enum Fn {
        ABS(1), SIN(1), COS(1), ASIN(1), ACOS(1), ATAN(1), ATAN2(2), SQRT(1), POW(2), EXP(1), LN(1),
        FLOOR(1), CEIL(1), ROUND(1), TRUNC(1), MIN(2), MAX(2), MOD(2), CLAMP(3), LERP(3), LERPROTATE(3),
        HERMITE_BLEND(1), RANDOM(2), RANDOM_INTEGER(2), DIE_ROLL(3), DIE_ROLL_INTEGER(3), SIGN(1),
        MIN_ANGLE(1);

        final int args;

        Fn(int args) {
            this.args = args;
        }
    }

    private static final class Call extends Expr {
        final Fn fn;
        final Expr[] args;

        Call(Fn fn, Expr[] args) {
            this.fn = fn;
            this.args = args;
        }

        @Override
        public double eval(Context c) {
            double a = args.length > 0 ? args[0].eval(c) : 0;
            double b = args.length > 1 ? args[1].eval(c) : 0;
            double d = args.length > 2 ? args[2].eval(c) : 0;
            return switch (fn) {
                case ABS -> Math.abs(a);
                case SIN -> Math.sin(Math.toRadians(a));
                case COS -> Math.cos(Math.toRadians(a));
                case ASIN -> Math.toDegrees(Math.asin(a));
                case ACOS -> Math.toDegrees(Math.acos(a));
                case ATAN -> Math.toDegrees(Math.atan(a));
                case ATAN2 -> Math.toDegrees(Math.atan2(a, b));
                case SQRT -> Math.sqrt(a);
                case POW -> Math.pow(a, b);
                case EXP -> Math.exp(a);
                case LN -> Math.log(a);
                case FLOOR -> Math.floor(a);
                case CEIL -> Math.ceil(a);
                case ROUND -> Math.round(a);
                case TRUNC -> a < 0 ? Math.ceil(a) : Math.floor(a);
                case MIN -> Math.min(a, b);
                case MAX -> Math.max(a, b);
                case MOD -> b == 0 ? 0 : a % b;
                case CLAMP -> Math.max(b, Math.min(d, a));
                case LERP -> a + (b - a) * d;
                case LERPROTATE -> {
                    double diff = wrap(b - a);
                    yield a + diff * d;
                }
                case HERMITE_BLEND -> 3 * a * a - 2 * a * a * a;
                case RANDOM -> a + ThreadLocalRandom.current().nextDouble() * (b - a);
                case RANDOM_INTEGER -> Math.floor(a + ThreadLocalRandom.current().nextDouble() * (Math.floor(b) - a + 1));
                case DIE_ROLL -> {
                    double sum = 0;
                    for (int i = 0; i < (int) Math.min(a, 64); i++) {
                        sum += b + ThreadLocalRandom.current().nextDouble() * (d - b);
                    }
                    yield sum;
                }
                case DIE_ROLL_INTEGER -> {
                    double sum = 0;
                    for (int i = 0; i < (int) Math.min(a, 64); i++) {
                        sum += Math.floor(b + ThreadLocalRandom.current().nextDouble() * (Math.floor(d) - b + 1));
                    }
                    yield sum;
                }
                case SIGN -> Math.signum(a);
                case MIN_ANGLE -> wrap(a);
            };
        }

        private static double wrap(double degrees) {
            double d = degrees % 360;
            if (d >= 180) {
                d -= 360;
            } else if (d < -180) {
                d += 360;
            }
            return d;
        }

        @Override
        public boolean isConstant() {
            return false;
        }

        @Override
        boolean usesQuery(Query q) {
            for (Expr e : args) {
                if (e.usesQuery(q)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** {@code name = value;} statements, then the value of {@code return} (or of the last statement). */
    private static final class Program extends Expr {
        final int[] targets;
        final Expr[] values;
        final Slots slots;
        final Expr result;

        Program(int[] targets, Expr[] values, Slots slots, Expr result) {
            this.targets = targets;
            this.values = values;
            this.slots = slots;
            this.result = result;
        }

        @Override
        public double eval(Context c) {
            for (int i = 0; i < targets.length; i++) {
                slots.values[targets[i]] = values[i].eval(c);
            }
            return result.eval(c);
        }

        @Override
        boolean usesQuery(Query q) {
            for (Expr e : values) {
                if (e.usesQuery(q)) {
                    return true;
                }
            }
            return result.usesQuery(q);
        }
    }

    // ----------------------------------------------------------------------------------------------- parser

    private static final class Parser {
        private final String text;
        private int pos;
        private final Map<String, Integer> variables = new HashMap<>();
        private final Slots slots = new Slots();

        Parser(String source) {
            if (source.length() > 4096) {
                throw new IllegalArgumentException("expression longer than 4096 characters");
            }
            this.text = source.toLowerCase(Locale.ROOT);
        }

        Expr program() {
            List<Integer> targets = new ArrayList<>();
            List<Expr> values = new ArrayList<>();
            Expr result = null;
            while (true) {
                skipSpace();
                if (pos >= text.length()) {
                    break;
                }
                if (peekWord("return")) {
                    pos += "return".length();
                    result = expression();
                    skipSpace();
                    if (peek(';')) {
                        pos++;
                    }
                    skipSpace();
                    if (pos < text.length()) {
                        throw error("nothing may follow 'return'");
                    }
                    break;
                }
                if (peekWord("loop") || peekWord("for_each") || peek('{')) {
                    throw error("loops and blocks aren't supported");
                }
                int start = pos;
                String name = tryAssignmentTarget();
                if (name != null) {
                    Expr value = expression();
                    targets.add(slot(name));
                    values.add(value);
                } else {
                    pos = start;
                    result = expression();
                }
                skipSpace();
                if (peek(';')) {
                    pos++;
                    continue;
                }
                skipSpace();
                if (pos < text.length()) {
                    throw error("unexpected '" + text.charAt(pos) + "'");
                }
                break;
            }
            if (result == null) {
                result = new Constant(0);
            }
            if (targets.isEmpty() && variables.isEmpty()) {
                return result;
            }
            slots.values = new double[variables.size()];
            return new Program(targets.stream().mapToInt(Integer::intValue).toArray(), values.toArray(new Expr[0]),
                    slots, result);
        }

        private int slot(String name) {
            return variables.computeIfAbsent(name, n -> variables.size());
        }

        /** {@code v.name =} (but not {@code ==}): the variable's name; otherwise null, position unspecified. */
        private String tryAssignmentTarget() {
            skipSpace();
            String name = variableName();
            if (name == null) {
                return null;
            }
            skipSpace();
            if (peek('=') && !(pos + 1 < text.length() && text.charAt(pos + 1) == '=')) {
                pos++;
                return name;
            }
            return null;
        }

        /** {@code variable.x}, {@code v.x}, {@code temp.x}, {@code t.x}: a key for the slot; else null. */
        private String variableName() {
            int start = pos;
            String word = identifier();
            if (word == null) {
                return null;
            }
            String[] split = word.split("\\.", 2);
            if (split.length == 2 && (split[0].equals("variable") || split[0].equals("v") || split[0].equals("temp")
                    || split[0].equals("t"))) {
                return split[1];
            }
            pos = start;
            return null;
        }

        Expr expression() {
            Expr test = coalesce();
            skipSpace();
            if (peek('?') && !(pos + 1 < text.length() && text.charAt(pos + 1) == '?')) {
                pos++;
                Expr then = expression();
                skipSpace();
                if (peek(':')) {
                    pos++;
                    Expr otherwise = expression();
                    return fold(new Conditional(test, then, otherwise));
                }
                // Bedrock's binary conditional: the value when true, else 0.
                return fold(new Conditional(test, then, new Constant(0)));
            }
            return test;
        }

        private Expr coalesce() {
            Expr left = or();
            while (true) {
                skipSpace();
                if (text.startsWith("??", pos)) {
                    pos += 2;
                    Expr right = or();
                    left = fold(new Binary(Op.COALESCE, left, right));
                } else {
                    return left;
                }
            }
        }

        private Expr or() {
            Expr left = and();
            while (true) {
                skipSpace();
                if (text.startsWith("||", pos)) {
                    pos += 2;
                    left = fold(new Binary(Op.OR, left, and()));
                } else {
                    return left;
                }
            }
        }

        private Expr and() {
            Expr left = equality();
            while (true) {
                skipSpace();
                if (text.startsWith("&&", pos)) {
                    pos += 2;
                    left = fold(new Binary(Op.AND, left, equality()));
                } else {
                    return left;
                }
            }
        }

        private Expr equality() {
            Expr left = comparison();
            while (true) {
                skipSpace();
                if (text.startsWith("==", pos)) {
                    pos += 2;
                    left = fold(new Binary(Op.EQ, left, comparison()));
                } else if (text.startsWith("!=", pos)) {
                    pos += 2;
                    left = fold(new Binary(Op.NE, left, comparison()));
                } else {
                    return left;
                }
            }
        }

        private Expr comparison() {
            Expr left = additive();
            while (true) {
                skipSpace();
                Op op;
                if (text.startsWith("<=", pos)) {
                    op = Op.LE;
                    pos += 2;
                } else if (text.startsWith(">=", pos)) {
                    op = Op.GE;
                    pos += 2;
                } else if (peek('<')) {
                    op = Op.LT;
                    pos++;
                } else if (peek('>')) {
                    op = Op.GT;
                    pos++;
                } else {
                    return left;
                }
                left = fold(new Binary(op, left, additive()));
            }
        }

        private Expr additive() {
            Expr left = multiplicative();
            while (true) {
                skipSpace();
                if (peek('+')) {
                    pos++;
                    left = fold(new Binary(Op.ADD, left, multiplicative()));
                } else if (peek('-')) {
                    pos++;
                    left = fold(new Binary(Op.SUB, left, multiplicative()));
                } else {
                    return left;
                }
            }
        }

        private Expr multiplicative() {
            Expr left = unary();
            while (true) {
                skipSpace();
                if (peek('*')) {
                    pos++;
                    left = fold(new Binary(Op.MUL, left, unary()));
                } else if (peek('/')) {
                    pos++;
                    left = fold(new Binary(Op.DIV, left, unary()));
                } else {
                    return left;
                }
            }
        }

        private Expr unary() {
            skipSpace();
            if (peek('-')) {
                pos++;
                return fold(new Negate(unary()));
            }
            if (peek('+')) {
                pos++;
                return unary();
            }
            if (peek('!')) {
                pos++;
                return fold(new Not(unary()));
            }
            return primary();
        }

        private Expr primary() {
            skipSpace();
            if (pos >= text.length()) {
                throw error("expression ends too early");
            }
            char c = text.charAt(pos);
            if (c == '(') {
                pos++;
                Expr inner = expression();
                skipSpace();
                expect(')');
                return inner;
            }
            if (Character.isDigit(c) || c == '.') {
                return number();
            }
            String word = identifier();
            if (word == null) {
                throw error("unexpected '" + c + "'");
            }
            if (word.equals("true")) {
                return new Constant(1);
            }
            if (word.equals("false")) {
                return new Constant(0);
            }
            String[] split = word.split("\\.", 2);
            String space = split[0];
            String name = split.length > 1 ? split[1] : "";
            skipSpace();
            boolean call = peek('(');
            List<Expr> args = new ArrayList<>();
            if (call) {
                pos++;
                skipSpace();
                if (!peek(')')) {
                    while (true) {
                        args.add(expression());
                        skipSpace();
                        if (peek(',')) {
                            pos++;
                            continue;
                        }
                        break;
                    }
                }
                skipSpace();
                expect(')');
            }
            switch (space) {
                case "math" -> {
                    if (name.equals("pi")) {
                        return new Constant(Math.PI);
                    }
                    Fn fn = null;
                    for (Fn f : Fn.values()) {
                        if (f.name().toLowerCase(Locale.ROOT).equals(name)) {
                            fn = f;
                        }
                    }
                    if (fn == null) {
                        return new Constant(0);
                    }
                    if (args.size() < fn.args) {
                        throw error("math." + name + " needs " + fn.args + " argument(s)");
                    }
                    return fold(new Call(fn, args.subList(0, fn.args).toArray(new Expr[0])));
                }
                case "query", "q" -> {
                    Query query = Query.byName(name);
                    return query == null ? new Constant(0) : new QueryNode(query);
                }
                case "variable", "v", "temp", "t" -> {
                    return new Variable(slots, slot(name));
                }
                default -> {
                    // context.*, geometry.*, texture.*, ...: nothing to read here.
                    return new Constant(0);
                }
            }
        }

        private Expr number() {
            int start = pos;
            while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
                pos++;
            }
            if (pos < text.length() && text.charAt(pos) == 'e') {
                int save = pos;
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '-' || text.charAt(pos) == '+')) {
                    pos++;
                }
                if (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                    while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                        pos++;
                    }
                } else {
                    pos = save;
                }
            }
            String digits = text.substring(start, pos);
            if (pos < text.length() && text.charAt(pos) == 'f') {
                pos++; // 1.5f
            }
            try {
                return new Constant(Double.parseDouble(digits));
            } catch (NumberFormatException e) {
                throw error("'" + digits + "' is not a number");
            }
        }

        /** A dotted name such as {@code math.sin} or {@code q.anim_time}; null when none starts here. */
        private String identifier() {
            int start = pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_' || (c == '.' && pos > start)) {
                    pos++;
                } else {
                    break;
                }
            }
            if (pos == start || !Character.isLetter(text.charAt(start)) && text.charAt(start) != '_') {
                pos = start;
                return null;
            }
            return text.substring(start, pos);
        }

        private boolean peekWord(String word) {
            if (!text.startsWith(word, pos)) {
                return false;
            }
            int end = pos + word.length();
            return end >= text.length() || !(Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_'
                    || text.charAt(end) == '.');
        }

        private boolean peek(char c) {
            return pos < text.length() && text.charAt(pos) == c;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw error("expected '" + c + "'");
            }
            pos++;
        }

        private void skipSpace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at character " + (pos + 1));
        }

        /** Works out an expression whose inputs are all constant. */
        private static Expr fold(Expr e) {
            boolean constant = switch (e) {
                case Binary b -> b.a.isConstant() && b.b.isConstant();
                case Not n -> n.a.isConstant();
                case Negate n -> n.a.isConstant();
                case Conditional c -> c.test.isConstant() && c.then.isConstant() && c.otherwise.isConstant();
                case Call c -> c.fn != Fn.RANDOM && c.fn != Fn.RANDOM_INTEGER && c.fn != Fn.DIE_ROLL
                        && c.fn != Fn.DIE_ROLL_INTEGER && java.util.Arrays.stream(c.args).allMatch(Expr::isConstant);
                default -> false;
            };
            return constant ? new Constant(e.eval(null)) : e;
        }
    }
}
