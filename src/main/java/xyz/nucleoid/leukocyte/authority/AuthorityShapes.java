package xyz.nucleoid.leukocyte.authority;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import xyz.nucleoid.leukocyte.shape.ProtectionShape;
import xyz.nucleoid.leukocyte.shape.UnionShape;
import xyz.nucleoid.stimuli.filter.EventFilter;

import java.util.Arrays;
import java.util.List;

public final class AuthorityShapes {
    public static final Codec<AuthorityShapes> CODEC = Entry.CODEC.listOf().xmap(
            AuthorityShapes::new,
            shapes -> Arrays.asList(shapes.entries)
    );

    public final Entry[] entries;
    private final UnionShape combinedShape;

    public AuthorityShapes(Entry... entries) {
        this.entries = entries;

        var enabledEntries = new java.util.ArrayList<ProtectionShape>();
        for (Entry entry : entries) {
            if (entry.enabled) {
                enabledEntries.add(entry.shape);
            }
        }
        this.combinedShape = new UnionShape(enabledEntries.toArray(new ProtectionShape[0]));
    }

    private AuthorityShapes(List<Entry> entries) {
        this(entries.toArray(new Entry[0]));
    }

    public AuthorityShapes withShape(String name, ProtectionShape shape) {
        var newShapes = Arrays.copyOf(this.entries, this.entries.length + 1);
        newShapes[newShapes.length - 1] = new Entry(name, shape);
        return new AuthorityShapes(newShapes);
    }

    public AuthorityShapes withShapeEnabled(String name, boolean enabled) {
        int index = this.findIndex(name);
        if (index == -1 || this.entries[index].enabled == enabled) {
            return this;
        }

        var newEntries = Arrays.copyOf(this.entries, this.entries.length);
        newEntries[index] = new Entry(newEntries[index].name, newEntries[index].shape, enabled);
        return new AuthorityShapes(newEntries);
    }

    public AuthorityShapes removeShape(String name) {
        int index = this.findIndex(name);
        if (index == -1) {
            return this;
        }

        int writer = 0;

        var newEntries = new Entry[this.entries.length - 1];
        for (int i = 0; i < this.entries.length; i++) {
            if (i != index) {
                newEntries[writer++] = this.entries[i];
            }
        }

        return new AuthorityShapes(newEntries);
    }

    private int findIndex(String name) {
        int index = -1;
        for (int i = 0; i < this.entries.length; i++) {
            if (this.entries[i].name.equals(name)) {
                index = i;
                break;
            }
        }
        return index;
    }

    public EventFilter asEventFilter() {
        return this.combinedShape.asEventFilter();
    }

    public Text displayList() {
        if (this.entries.length == 0) {
            return Text.literal("Empty\n").formatted(Formatting.YELLOW);
        }

        MutableText text = Text.literal("");
        for (var entry : this.entries) {
            text = text.append(Text.literal("  " + entry.name).formatted(Formatting.AQUA))
                    .append(": ")
                    .append(entry.shape.displayShort())
                    .append(entry.enabled ? "" : " (disabled)")
                    .append("\n");
        }

        return text;
    }

    public Text displayShort() {
        return this.combinedShape.displayShort();
    }

    public boolean isEmpty() {
        return this.entries.length == 0;
    }

    public record Entry(String name, ProtectionShape shape, boolean enabled) {
        public Entry(String name, ProtectionShape shape) {
            this(name, shape, true);
        }

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> {
            return instance.group(
                    Codec.STRING.fieldOf("name").forGetter(entry -> entry.name),
                    ProtectionShape.CODEC.fieldOf("shape").forGetter(entry -> entry.shape),
                    Codec.BOOL.optionalFieldOf("enabled", true).forGetter(entry -> entry.enabled)
            ).apply(instance, Entry::new);
        });
    }
}
