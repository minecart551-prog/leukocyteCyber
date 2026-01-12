package xyz.nucleoid.leukocyte.authority;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.player.PlayerEntity;
import xyz.nucleoid.leukocyte.Leukocyte;
import xyz.nucleoid.leukocyte.rule.*;
import xyz.nucleoid.leukocyte.shape.ProtectionShape;
import xyz.nucleoid.stimuli.event.EventListenerMap;
import xyz.nucleoid.stimuli.filter.EventFilter;

// TODO: support specific exclusions of a list of players by API?
public final class Authority implements Comparable<Authority> {
    public static final Codec<Authority> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Codec.STRING.fieldOf("key").forGetter(authority -> authority.key),
                Codec.INT.fieldOf("level").forGetter(authority -> authority.level),
                AuthorityShapes.CODEC.fieldOf("shapes").forGetter(authority -> authority.shapes),
                ProtectionRuleMap.CODEC.fieldOf("rules").forGetter(authority -> authority.rules),
                ProtectionExclusions.CODEC.fieldOf("exclusions").forGetter(authority -> authority.exclusions),
                ProtectionInclusions.CODEC.fieldOf("inclusions").forGetter(authority -> authority.included)
                ).apply(instance, Authority::new);
    });

    private final String key;
    private final int level;
    private final AuthorityShapes shapes;
    private final ProtectionRuleMap rules;
    private final ProtectionExclusions exclusions;
    private final ProtectionInclusions included;
    private final EventListenerMap eventListeners;

    private final EventFilter eventFilter;

    Authority(String key, int level, AuthorityShapes shapes, ProtectionRuleMap rules, ProtectionExclusions exclusions, ProtectionInclusions included) {
        this.key = key;
        this.level = level;
        this.shapes = shapes;
        this.rules = rules;
        this.exclusions = exclusions;
        this.included = included;
        this.eventListeners = Leukocyte.createEventListenersFor(rules);
        this.eventFilter = this.applyToFilter(shapes.asEventFilter());
    }

    public EventFilter applyToFilter(EventFilter filter) {
        return source -> {
            if (filter.accepts(source)) {
                var entity = source.getEntity();
                if (entity instanceof PlayerEntity player) {
                    if (player.hasPermissionLevel(4)) return false;

                    if (!this.included.isEmpty())
                        return (this.included.isIncluded(player));
                    return !(this.exclusions.isExcluded(player));
                }
            }
            return false;
        };
    }

    Authority(String key, int level, AuthorityShapes shapes) {
        this(key, level, shapes, new ProtectionRuleMap(), new ProtectionExclusions(), new ProtectionInclusions());
    }

    public static Authority create(String key) {
        return new Authority(key, 0, new AuthorityShapes());
    }

    public Authority withLevel(int level) {
        return new Authority(this.key, level, this.shapes, this.rules, this.exclusions.copy(), this.included.copy());
    }

    public Authority withRule(ProtectionRule rule, RuleResult result) {
        return new Authority(this.key, this.level, this.shapes, this.rules.with(rule, result), this.exclusions.copy(), this.included.copy());
    }

    public Authority addShape(String name, ProtectionShape shape) {
        var newShapes = this.shapes.withShape(name, shape);
        return new Authority(this.key, this.level, newShapes, this.rules, this.exclusions.copy(), this.included.copy());
    }

    public Authority removeShape(String name) {
        var newShapes = this.shapes.removeShape(name);
        if (this.shapes == newShapes) {
            return this;
        }
        return new Authority(this.key, this.level, newShapes, this.rules, this.exclusions.copy(), this.included.copy());
    }

    public String getKey() {
        return this.key;
    }

    public int getLevel() {
        return this.level;
    }

    public AuthorityShapes getShapes() {
        return this.shapes;
    }

    public ProtectionRuleMap getRules() {
        return this.rules;
    }

    public ProtectionExclusions getExclusions() {
        return this.exclusions;
    }

    public ProtectionInclusions getIncluded() {
        return this.included;
    }

    public EventListenerMap getEventListeners() {
        return this.eventListeners;
    }

    public EventFilter getEventFilter() {
        return this.eventFilter;
    }

    @Override
    public int compareTo(Authority other) {
        int levelCompare = Integer.compare(other.level, this.level);
        if (levelCompare != 0) {
            return levelCompare;
        } else {
            return this.key.compareTo(other.key);
        }
    }

    @Override
    public int hashCode() {
        return this.key.hashCode();
    }
}
