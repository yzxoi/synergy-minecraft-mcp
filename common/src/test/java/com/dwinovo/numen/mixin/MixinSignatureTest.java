package com.dwinovo.numen.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards client mixin callback descriptors that Java compilation alone cannot validate. */
class MixinSignatureTest {

    @Test
    void chatScreenKeyInjectionMatchesThe12111InputEventSignature() throws Exception {
        assertEquals(boolean.class,
                ChatScreen.class.getDeclaredMethod("keyPressed", KeyEvent.class).getReturnType());

        // The headless common test runtime intentionally omits Mixin itself, so inspect the
        // emitted class descriptor without loading MixinChatScreen and its callback types.
        try (var in = MixinSignatureTest.class.getResourceAsStream("/com/dwinovo/numen/mixin/MixinChatScreen.class")) {
            assertNotNull(in);
            String classFile = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertTrue(classFile.contains("(Lnet/minecraft/client/input/KeyEvent;"
                    + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V"));
        }
    }
}
