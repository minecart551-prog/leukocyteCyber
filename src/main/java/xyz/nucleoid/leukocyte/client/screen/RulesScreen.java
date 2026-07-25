package xyz.nucleoid.leukocyte.client.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import xyz.nucleoid.leukocyte.client.network.ClientPacketHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RulesScreen extends Screen {
    private static final String[] ALL_RULES = {
            "break", "place", "block_drops",
            "interact_blocks", "interact_entities", "interact_items", "interact",
            "attack", "pvp", "spectate_entities",
            "portals", "crafting", "regeneration", "hunger",
            "fall_damage", "fire_damage", "freezing_damage", "lava_damage", "damage",
            "throw_items", "pickup_items",
            "unstable_tnt", "ignite_tnt", "firework_explode", "dispenser_activate", "spawn_wither",
            "fire_tick", "fluid_flow", "ice_melt", "snow_fall", "coral_death",
            "spawn_animals", "spawn_monsters",
            "throw_projectiles", "shear_entities",
            "explosion",
            "block_random_tick", "fluid_random_tick"
    };

    private static final String[] RULE_CATEGORIES = {
            "Blocks", "Interaction", "Combat", "World", "Damage", "Items", "Explosives", "Environment", "Spawning", "Projectiles", "Misc", "Ticks"
    };

    private final String authorityKey;
    private final Map<String, String> currentRules = new LinkedHashMap<>();
    private final ArrayList<String> filteredRules = new ArrayList<>();
    private TextFieldWidget searchField;
    private int scrollOffset = 0;

    public RulesScreen(String authorityKey) {
        super(Text.literal("Rules: " + authorityKey));
        this.authorityKey = authorityKey;
    }

    @Override
    protected void init() {
        searchField = new TextFieldWidget(textRenderer, width / 2 - 100, 32, 200, 20, Text.literal(""));
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
            client.setScreen(new AuthorityDetailScreen(authorityKey));
        }).dimensions(width / 2 - 49, height - 32, 98, 20).build());

        ClientPacketHandler.setDetailCallback(this::onDetailReceived);
        ClientPacketHandler.requestDetail(authorityKey);

        for (String rule : ALL_RULES) {
            filteredRules.add(rule);
        }
    }

    @Override
    public void close() {
        ClientPacketHandler.setDetailCallback(null);
        client.setScreen(null);
    }

    private void onSearchChanged(String query) {
        filteredRules.clear();
        String lower = query.toLowerCase();
        for (String rule : ALL_RULES) {
            if (lower.isEmpty() || rule.toLowerCase().contains(lower)) {
                filteredRules.add(rule);
            }
        }
        scrollOffset = 0;
    }

    private void onDetailReceived(NbtCompound data) {
        currentRules.clear();
        if (data.contains("rules", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
            var rulesTag = data.getCompound("rules");
            for (String key : rulesTag.getKeys()) {
                currentRules.put(key, rulesTag.getString(key));
            }
        }
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);

        if (searchField != null) {
            searchField.render(context, mouseX, mouseY, delta);
        }

        int listTop = 58;
        int listBottom = height - 40;
        int entryHeight = 14;
        int maxVisible = (listBottom - listTop) / entryHeight;

        int startIdx = scrollOffset;
        int endIdx = Math.min(startIdx + maxVisible, filteredRules.size());

        for (int i = startIdx; i < endIdx; i++) {
            String rule = filteredRules.get(i);
            int y = listTop + (i - startIdx) * entryHeight;

            boolean hovered = mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + entryHeight;
            if (hovered) {
                context.fill(10, y, width - 10, y + entryHeight, 0x40FFFFFF);
            }

            String result = currentRules.getOrDefault(rule, "pass");
            int resultColor = switch (result) {
                case "allow" -> 0x55FF55;
                case "deny" -> 0xFF5555;
                default -> 0xFFFF55;
            };

            context.drawTextWithShadow(textRenderer, rule, 14, y + 3, 0xAAAAAA);
            context.drawTextWithShadow(textRenderer, result, width - 60, y + 3, resultColor);

            if (hovered) {
                context.drawTextWithShadow(textRenderer, "[Click to cycle]", width / 2 - 30, y + 3, 0x888888);
            }
        }

        if (filteredRules.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No rules match search", width / 2, listTop + 20, 0x888888);
        }

        String scrollText = filteredRules.isEmpty() ? "" : (startIdx + 1) + "-" + endIdx + " of " + filteredRules.size();
        context.drawCenteredTextWithShadow(textRenderer, scrollText, width / 2, listBottom + 4, 0x888888);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int entryHeight = 14;
        int listBottom = height - 40;
        int listTop = 58;
        int maxVisible = (listBottom - listTop) / entryHeight;

        if (amount < 0 && scrollOffset < Math.max(0, filteredRules.size() - maxVisible)) {
            scrollOffset++;
        } else if (amount > 0 && scrollOffset > 0) {
            scrollOffset--;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listTop = 58;
            int entryHeight = 14;
            int listBottom = height - 40;
            int maxVisible = (listBottom - listTop) / entryHeight;
            int startIdx = scrollOffset;
            int endIdx = Math.min(startIdx + maxVisible, filteredRules.size());

            for (int i = startIdx; i < endIdx; i++) {
                int y = listTop + (i - startIdx) * entryHeight;
                if (mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + entryHeight) {
                    String rule = filteredRules.get(i);
                    String current = currentRules.getOrDefault(rule, "pass");
                    String next = switch (current) {
                        case "pass" -> "allow";
                        case "allow" -> "deny";
                        default -> "pass";
                    };
                    ClientPacketHandler.setRule(authorityKey, rule, next);
                    currentRules.put(rule, next);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
