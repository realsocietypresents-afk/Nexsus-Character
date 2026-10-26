package com.nexuscraft.nexuscharacter;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/** What a perk does: a steady potion effect, a bonus on an attribute, or extra experience. Written in config.yml as one line each. */
sealed interface Effect {

    /** {@code potion:HASTE:0} */
    record Potion(PotionEffectType type, String name, int amplifier) implements Effect {
    }

    /** {@code attribute:MAX_HEALTH:2:add} (op: add, scalar or multiply). */
    record Attr(Attribute attribute, String name, double amount, AttributeModifier.Operation op) implements Effect {
    }

    /** {@code xp:10}: percent extra experience from orbs. */
    record Xp(double percent) implements Effect {
    }

    /** Parses one effect line; throws IllegalArgumentException saying what is wrong. */
    static Effect parse(String text) {
        String[] p = text.trim().split(":");
        String kind = p[0].trim().toLowerCase(Locale.ROOT);
        try {
            switch (kind) {
                case "potion" -> {
                    if (p.length < 2 || p.length > 3) {
                        throw new IllegalArgumentException("potion needs a name and an optional level, like potion:HASTE:0");
                    }
                    String name = p[1].trim().toUpperCase(Locale.ROOT);
                    NamespacedKey key = NamespacedKey.fromString("minecraft:" + name.toLowerCase(Locale.ROOT));
                    PotionEffectType type = key == null ? null : Registry.EFFECT.get(key);
                    if (type == null) {
                        throw new IllegalArgumentException("no potion effect called " + name);
                    }
                    int amp = p.length == 3 ? Integer.parseInt(p[2].trim()) : 0;
                    return new Potion(type, name, Math.max(0, Math.min(9, amp)));
                }
                case "attribute" -> {
                    if (p.length < 3 || p.length > 4) {
                        throw new IllegalArgumentException("attribute needs a name and an amount, like attribute:MAX_HEALTH:2:add");
                    }
                    String name = p[1].trim().toUpperCase(Locale.ROOT);
                    Attribute a = attribute(name);
                    if (a == null) {
                        throw new IllegalArgumentException("no attribute called " + name);
                    }
                    double amount = Double.parseDouble(p[2].trim());
                    if (Double.isNaN(amount) || Double.isInfinite(amount) || amount == 0) {
                        throw new IllegalArgumentException("the amount must be a number other than 0");
                    }
                    String op = p.length == 4 ? p[3].trim().toLowerCase(Locale.ROOT) : "add";
                    AttributeModifier.Operation operation = switch (op) {
                        case "add" -> AttributeModifier.Operation.ADD_NUMBER;
                        case "scalar" -> AttributeModifier.Operation.ADD_SCALAR;
                        case "multiply" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
                        default -> throw new IllegalArgumentException("the way to apply it must be add, scalar or multiply");
                    };
                    return new Attr(a, name, amount, operation);
                }
                case "xp" -> {
                    if (p.length != 2) {
                        throw new IllegalArgumentException("xp needs a percentage, like xp:10");
                    }
                    double pct = Double.parseDouble(p[1].trim());
                    if (Double.isNaN(pct) || pct <= 0 || pct > 500) {
                        throw new IllegalArgumentException("the xp percentage must be between 0 and 500");
                    }
                    return new Xp(pct);
                }
                default -> throw new IllegalArgumentException("unknown effect '" + p[0].trim() + "' (use potion, attribute or xp)");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text.trim() + "' has a number that is not a number");
        }
    }

    /** Finds an attribute by its short name (MAX_HEALTH) whatever the server version calls the constant (GENERIC_MAX_HEALTH on older ones). */
    static Attribute attribute(String name) {
        for (String prefix : new String[]{"GENERIC_", "PLAYER_", ""}) {
            try {
                Object o = Attribute.class.getField(prefix + name).get(null);
                if (o instanceof Attribute a) {
                    return a;
                }
            } catch (ReflectiveOperationException | RuntimeException notThis) {
                // try the next spelling
            }
        }
        return null;
    }

    /** The effect in words, with its size scaled by prestige. */
    default String describe(double scale) {
        if (this instanceof Potion p) {
            return pretty(p.name()) + (p.amplifier() > 0 ? " " + roman(p.amplifier() + 1) : "");
        }
        if (this instanceof Xp x) {
            return "+" + num(x.percent() * scale) + "% experience";
        }
        Attr a = (Attr) this;
        double v = a.amount() * scale;
        String sign = v >= 0 ? "+" : "-";
        double abs = Math.abs(v);
        if (a.op() == AttributeModifier.Operation.ADD_NUMBER) {
            if (a.name().equals("MAX_HEALTH")) {
                return sign + num(abs / 2.0) + (Math.abs(abs / 2.0 - 1.0) < 0.005 ? " heart" : " hearts");
            }
            return sign + num(abs) + " " + pretty(a.name());
        }
        return sign + num(abs * 100.0) + "% " + pretty(a.name());
    }

    private static String num(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    private static String pretty(String name) {
        return name.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }
}
