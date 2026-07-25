package xyz.nucleoid.leukocyte.client.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import xyz.nucleoid.leukocyte.client.network.ClientPacketHandler;

import java.util.ArrayList;

public final class AuthorityDetailScreen extends Screen {
    private final String authorityKey;
    private int level = 0;
    private ArrayList<String> shapeNames = new ArrayList<>();
    private ArrayList<String> shapeDisplays = new ArrayList<>();
    private ArrayList<NbtCompound> shapeDataList = new ArrayList<>();
    private int ruleCount = 0;
    private ArrayList<String> exclusionRoles = new ArrayList<>();
    private ArrayList<String> exclusionPerms = new ArrayList<>();
    private ArrayList<String> inclusionRoles = new ArrayList<>();
    private ArrayList<String> inclusionPerms = new ArrayList<>();

    public AuthorityDetailScreen(String authorityKey) {
        super(Text.literal("Authority: " + authorityKey));
        this.authorityKey = authorityKey;
    }

    @Override
    protected void init() {
        ClientPacketHandler.setDetailCallback(this::onDetailReceived);

        int btnWidth = 98;
        int btnHeight = 20;
        int startY = 60;
        int gap = 4;
        int col1 = width / 2 - btnWidth - 2;
        int col2 = width / 2 + 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Shapes (" + shapeNames.size() + ")"), button -> {
            client.setScreen(new ShapesScreen(authorityKey, shapeNames, shapeDisplays, shapeDataList));
        }).dimensions(col1, startY, btnWidth, btnHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Rules (" + ruleCount + ")"), button -> {
            client.setScreen(new RulesScreen(authorityKey));
        }).dimensions(col2, startY, btnWidth, btnHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Exclusions"), button -> {
            client.setScreen(new ExclusionsScreen(authorityKey, exclusionRoles, exclusionPerms, false));
        }).dimensions(col1, startY + btnHeight + gap, btnWidth, btnHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Inclusions"), button -> {
            client.setScreen(new InclusionsScreen(authorityKey, inclusionRoles, inclusionPerms));
        }).dimensions(col2, startY + btnHeight + gap, btnWidth, btnHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Set Level"), button -> {
            client.setScreen(new TextInputScreen("Set Level", "Level (current: " + level + "):", value -> {
                try {
                    int newLevel = Integer.parseInt(value.trim());
                    ClientPacketHandler.setLevel(authorityKey, newLevel);
                    refreshDetail();
                } catch (NumberFormatException e) {
                }
            }));
        }).dimensions(col1, startY + (btnHeight + gap) * 2, btnWidth, btnHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Test Rules Here"), button -> {
            ClientPacketHandler.testRules();
        }).dimensions(col2, startY + (btnHeight + gap) * 2, btnWidth, btnHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Delete Authority"), button -> {
            client.setScreen(new ConfirmScreen("Delete '" + authorityKey + "'?", confirmed -> {
                if (confirmed) {
                    ClientPacketHandler.removeAuthority(authorityKey);
                    client.setScreen(null);
                } else {
                    client.setScreen(this);
                }
            }));
        }).dimensions(col1, startY + (btnHeight + gap) * 3, btnWidth, btnHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
            client.setScreen(new LeukocyteScreen());
        }).dimensions(col2, startY + (btnHeight + gap) * 3, btnWidth, btnHeight).build());

        ClientPacketHandler.requestDetail(authorityKey);
    }

    @Override
    public void close() {
        ClientPacketHandler.setDetailCallback(null);
        client.setScreen(null);
    }

    private void refreshDetail() {
        ClientPacketHandler.requestDetail(authorityKey);
    }

    private void onDetailReceived(NbtCompound data) {
        this.level = data.getInt("level");

        shapeNames.clear();
        shapeDisplays.clear();
        shapeDataList.clear();
        if (data.contains("shapes", NbtElement.LIST_TYPE)) {
            var shapesList = data.getList("shapes", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < shapesList.size(); i++) {
                var entry = shapesList.getCompound(i);
                shapeNames.add(entry.getString("name"));
                if (entry.contains("shape", NbtElement.COMPOUND_TYPE)) {
                    var shapeData = entry.getCompound("shape");
                    shapeDataList.add(shapeData.copy());
                    String type = shapeData.contains("type") ? shapeData.getString("type") : "unknown";
                    if (type.equals("union")) {
                        int subCount = shapeData.contains("value", NbtElement.LIST_TYPE)
                                ? shapeData.getList("value", NbtElement.COMPOUND_TYPE).size() : 0;
                        shapeDisplays.add(subCount + " combined shapes");
                    } else {
                        shapeDisplays.add(type);
                    }
                } else {
                    shapeDataList.add(new NbtCompound());
                    shapeDisplays.add("unknown");
                }
            }
        }

        ruleCount = 0;
        if (data.contains("rules", NbtElement.COMPOUND_TYPE)) {
            ruleCount = data.getCompound("rules").getKeys().size();
        }

        exclusionRoles.clear();
        exclusionPerms.clear();
        if (data.contains("exclusions", NbtElement.COMPOUND_TYPE)) {
            var exc = data.getCompound("exclusions");
            if (exc.contains("roles", NbtElement.LIST_TYPE)) {
                var roles = exc.getList("roles", NbtElement.STRING_TYPE);
                for (int i = 0; i < roles.size(); i++) {
                    exclusionRoles.add(roles.getString(i));
                }
            }
            if (exc.contains("permissions", NbtElement.LIST_TYPE)) {
                var perms = exc.getList("permissions", NbtElement.STRING_TYPE);
                for (int i = 0; i < perms.size(); i++) {
                    exclusionPerms.add(perms.getString(i));
                }
            }
        }

        inclusionRoles.clear();
        inclusionPerms.clear();
        if (data.contains("inclusions", NbtElement.COMPOUND_TYPE)) {
            var inc = data.getCompound("inclusions");
            if (inc.contains("roles", NbtElement.LIST_TYPE)) {
                var roles = inc.getList("roles", NbtElement.STRING_TYPE);
                for (int i = 0; i < roles.size(); i++) {
                    inclusionRoles.add(roles.getString(i));
                }
            }
            if (inc.contains("permissions", NbtElement.LIST_TYPE)) {
                var perms = inc.getList("permissions", NbtElement.STRING_TYPE);
                for (int i = 0; i < perms.size(); i++) {
                    inclusionPerms.add(perms.getString(i));
                }
            }
        }
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "Level: " + level, width / 2, 30, 0xAAAAAA);

        String shapeSummary = shapeNames.isEmpty() ? "None" : String.join(", ", shapeNames);
        context.drawCenteredTextWithShadow(textRenderer, "Shapes: " + shapeSummary, width / 2, 42, 0x888888);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
