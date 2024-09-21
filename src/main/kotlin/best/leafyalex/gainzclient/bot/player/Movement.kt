package best.leafyalex.gainzclient.bot.player

import best.leafyalex.gainzclient.GainzClient
import best.leafyalex.gainzclient.utils.RandomUtils
import best.leafyalex.gainzclient.utils.TimeUtils
import net.minecraft.client.settings.KeyBinding

object Movement {
    private var forward = false
    private var backward = false
    private var left = false
    private var right = false
    private var jumping = false
    private var sprinting = false
    private var sneaking = false

    fun startForward() {
        if (GainzClient.bot?.toggled() == true) { // need to do this because the type is Boolean? so it could be null
            forward = true
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindForward.keyCode, true)
        }
    }

    fun stopForward() {
        if (GainzClient.bot?.toggled() == true) {
            forward = false
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindForward.keyCode, false)
        }
    }

    fun startBackward() {
        if (GainzClient.bot?.toggled() == true) {
            backward = true
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindBack.keyCode, true)
        }
    }

    fun stopBackward() {
        if (GainzClient.bot?.toggled() == true) {
            backward = false
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindBack.keyCode, false)
        }
    }

    fun startLeft() {
        if (GainzClient.bot?.toggled() == true) {
            left = true
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindLeft.keyCode, true)
        }
    }

    fun stopLeft() {
        if (GainzClient.bot?.toggled() == true) {
            left = false
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindLeft.keyCode, false)
        }
    }

    fun startRight() {
        if (GainzClient.bot?.toggled() == true) {
            right = true
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindRight.keyCode, true)
        }
    }

    fun stopRight() {
        if (GainzClient.bot?.toggled() == true) {
            right = false
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindRight.keyCode, false)
        }
    }

    fun startJumping() {
        if (GainzClient.bot?.toggled() == true) {
            jumping = true
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindJump.keyCode, true)
        }
    }

    fun stopJumping() {
        if (GainzClient.bot?.toggled() == true) {
            jumping = false
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindJump.keyCode, false)
        }
    }

    fun startSprinting() {
        if (GainzClient.bot?.toggled() == true) {
            sprinting = true
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindSprint.keyCode, true)
        }
    }

    fun stopSprinting() {
        if (GainzClient.bot?.toggled() == true) {
            sprinting = false
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindSprint.keyCode, false)
        }
    }

    fun startSneaking() {
        if (GainzClient.bot?.toggled() == true) {
            sneaking = true
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindSneak.keyCode, true)
        }
    }

    fun stopSneaking() {
        if (GainzClient.bot?.toggled() == true) {
            sneaking = false
            KeyBinding.setKeyBindState(GainzClient.mc.gameSettings.keyBindSneak.keyCode, false)
        }
    }

    fun singleJump(holdDuration: Int) {
        startJumping()
        TimeUtils.setTimeout(this::stopJumping, holdDuration)
    }

    fun clearAll() {
        stopForward()
        stopBackward()
        stopLeft()
        stopRight()
        stopJumping()
        stopSprinting()
        stopSneaking()
    }

    fun clearLeftRight() {
        stopLeft()
        stopRight()
    }

    fun swapLeftRight() {
        if (left) {
            stopLeft()
            startRight()
        } else if (right) {
            stopRight()
            startLeft()
        }
    }

    fun forward(): Boolean {
        return forward
    }

    fun backward(): Boolean {
        return backward
    }

    fun left(): Boolean {
        return left
    }

    fun right(): Boolean {
        return right
    }

    fun jumping(): Boolean {
        return jumping
    }

    fun sprinting(): Boolean {
        return sprinting
    }

    fun sneaking(): Boolean {
        return sneaking
    }

}