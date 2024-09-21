package best.leafyalex.gainzclient.core

import best.leafyalex.gainzclient.GainzClient
import gg.essential.api.EssentialAPI
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import org.lwjgl.input.Keyboard

object KeyBindings {

    val toggleBotKeyBinding = KeyBinding("Toggle", Keyboard.KEY_SEMICOLON, "GainzClient")
    val configGuiKeyBinding = KeyBinding("Open da Gui", Keyboard.KEY_RSHIFT, "GainzClient")

    fun register() {
        ClientRegistry.registerKeyBinding(toggleBotKeyBinding)
        ClientRegistry.registerKeyBinding(configGuiKeyBinding)
    }

    @SubscribeEvent
    fun onTick(ev: ClientTickEvent) {
        if (configGuiKeyBinding.isPressed) {
            EssentialAPI.getGuiUtil().openScreen(GainzClient.config?.gui())
        }
    }

}