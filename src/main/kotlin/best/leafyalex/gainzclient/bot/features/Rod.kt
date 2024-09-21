package best.leafyalex.gainzclient.bot.features

import best.leafyalex.gainzclient.bot.StateManager
import best.leafyalex.gainzclient.bot.player.Inventory
import best.leafyalex.gainzclient.bot.player.Mouse
import best.leafyalex.gainzclient.utils.RandomUtils
import best.leafyalex.gainzclient.utils.TimeUtils
import net.minecraft.client.Minecraft

interface Rod {

    private val mc_: Minecraft
        get() = Minecraft.getMinecraft()

    fun useRod() {
        Mouse.stopLeftAC()
        Mouse.setUsingProjectile(true)
        TimeUtils.setTimeout(fun () {
            Inventory.setInvItem("rod")
            TimeUtils.setTimeout(fun () {
                val r = RandomUtils.randomIntInRange(100, 200)
                Mouse.rClick(r)
                TimeUtils.setTimeout(fun () {
                    Mouse.setUsingProjectile(false)
                }, RandomUtils.randomIntInRange(30, 70))
                TimeUtils.setTimeout(fun () {
                    if (mc_.thePlayer.heldItem != null && !mc_.thePlayer.heldItem.unlocalizedName.lowercase().contains("bow")) {
                        Inventory.setInvItem("sword")
                    }
                    TimeUtils.setTimeout(fun () {
                        if (StateManager.state == StateManager.States.PLAYING) {
                            Mouse.startLeftAC()
                        }
                    }, RandomUtils.randomIntInRange(100, 150))
                }, r + RandomUtils.randomIntInRange(250, 400))
            }, RandomUtils.randomIntInRange(50, 90))
        }, RandomUtils.randomIntInRange(10, 30))
    }

}