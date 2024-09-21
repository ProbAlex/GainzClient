package best.leafyalex.gainzclient.core

import gg.essential.vigilance.data.Category
import gg.essential.vigilance.data.SortingBehavior

class ConfigSorter : SortingBehavior() {

    private val items = arrayListOf(
        "General",
        "Combat",
        "Queue Dodging",
        "Auto Requeue",
        "AutoGG",
        "Webhook",
        "Misc",
        "Remote Control"
    )

    override fun getCategoryComparator(): Comparator<in Category> = compareBy { items.indexOf(it.name) }

}