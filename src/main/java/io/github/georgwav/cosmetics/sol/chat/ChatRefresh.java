package io.github.georgwav.cosmetics.sol.chat;

/** The chat (through SolChatMixin): rebuild the lines it shows, after Sol Chat's tabs or search changed. */
public interface ChatRefresh {
    void cosmeticsmod$refresh();
}
