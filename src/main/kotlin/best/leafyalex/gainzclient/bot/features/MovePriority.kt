package best.leafyalex.gainzclient.bot.features

import best.leafyalex.gainzclient.bot.player.Combat
import best.leafyalex.gainzclient.bot.player.Movement
import best.leafyalex.gainzclient.utils.RandomUtils

interface MovePriority {

    fun handle(clear: Boolean, randomStrafe: Boolean, movePriority: ArrayList<Int>) {
        if (clear) {
            Combat.stopRandomStrafe()
            Movement.clearLeftRight()
        } else {
            if (randomStrafe) {
                Combat.startRandomStrafe(1000, 2000)
            } else {
                Combat.stopRandomStrafe()
                if (movePriority[0] > movePriority[1]) {
                    Movement.stopRight()
                    Movement.startLeft()
                } else if (movePriority[1] > movePriority[0]) {
                    Movement.stopLeft()
                    Movement.startRight()
                } else {
                    if (RandomUtils.randomBool()) {
                        Movement.startLeft()
                    } else {
                        Movement.startRight()
                    }
                }
            }
        }
    }

}