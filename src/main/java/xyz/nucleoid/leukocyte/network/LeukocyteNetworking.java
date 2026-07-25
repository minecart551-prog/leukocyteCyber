package xyz.nucleoid.leukocyte.network;

import net.minecraft.util.Identifier;

public final class LeukocyteNetworking {
    public static final Identifier C2S_CHANNEL = new Identifier("leukocyte", "c2s");
    public static final Identifier S2C_CHANNEL = new Identifier("leukocyte", "s2c");

    public static final byte ACTION_REQUEST_ALL = 0;
    public static final byte ACTION_REQUEST_DETAIL = 1;
    public static final byte ACTION_ADD_AUTHORITY = 2;
    public static final byte ACTION_REMOVE_AUTHORITY = 3;
    public static final byte ACTION_SET_LEVEL = 4;
    public static final byte ACTION_SET_RULE = 5;
    public static final byte ACTION_ADD_SHAPE = 6;
    public static final byte ACTION_REMOVE_SHAPE = 7;
    public static final byte ACTION_SET_SHAPE = 14;
    public static final byte ACTION_ADD_EXCLUSION = 8;
    public static final byte ACTION_REMOVE_EXCLUSION = 9;
    public static final byte ACTION_ADD_INCLUSION = 10;
    public static final byte ACTION_REMOVE_INCLUSION = 11;
    public static final byte ACTION_TEST_RULES = 12;

    public static final byte ACTION_REQUEST_SHAPE_TOOL_DATA = 15;
    public static final byte ACTION_CREATE_BOX_SHAPE = 16;
    public static final byte ACTION_RENAME_SHAPE = 17;
    public static final byte ACTION_COMBINE_SHAPES = 18;
    public static final byte ACTION_TELEPORT_TO_SHAPE = 19;
    public static final byte ACTION_SUBTRACT_BOX = 20;
    public static final byte ACTION_ADD_BOX_TO_SHAPE = 21;

    public static final byte RESPONSE_AUTHORITY_LIST = 0;
    public static final byte RESPONSE_AUTHORITY_DETAIL = 1;
    public static final byte RESPONSE_RESULT = 2;
    public static final byte RESPONSE_TEST_RESULT = 3;
    public static final byte RESPONSE_OPEN_SCREEN = 4;
    public static final byte RESPONSE_SHAPE_TOOL_DATA = 5;

    public static final byte EXCLUSION_TYPE_PLAYER = 0;
    public static final byte EXCLUSION_TYPE_ROLE = 1;
    public static final byte EXCLUSION_TYPE_PERMISSION = 2;

    private LeukocyteNetworking() {
    }
}
