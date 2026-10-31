package dev.tokensmp.sound;

public enum SoundType {
    TOKEN_RECEIVED("token-received"),
    TOKEN_LOST("token-lost"),
    BOUNTY_PLACED("bounty-placed"),
    BOUNTY_CLAIMED("bounty-claimed"),
    SUCCESS("success"),
    DECLINED("declined"),
    MENU_OPEN("menu-open"),
    MENU_CLOSE("menu-close"),
    FIRST_JOIN("first-join");

    public final String configKey;

    SoundType(String configKey) {
        this.configKey = configKey;
    }
}
