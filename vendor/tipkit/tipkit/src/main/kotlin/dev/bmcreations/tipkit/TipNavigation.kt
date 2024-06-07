package dev.bmcreations.tipkit

interface TipActionNavigation {
    fun onActionClicked(action: TipAction)
}

class NoOpTipNavigator : TipActionNavigation {
    override fun onActionClicked(action: TipAction) = Unit

}