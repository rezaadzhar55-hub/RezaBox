package io.nekohasekai.sagernet.ktx

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.MainActivity

class FixedLinearLayoutManager(val recyclerView: RecyclerView) :
    LinearLayoutManager(recyclerView.context, RecyclerView.VERTICAL, false) {

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (ignored: IndexOutOfBoundsException) {
        }
    }

    private var listenerDisabled = false

    override fun scrollVerticallyBy(
        dx: Int, recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        // Matsuri style
        if (!DataStore.showBottomBar) return super.scrollVerticallyBy(dx, recycler, state)

        // SagerNet Style
        val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
        return scrollRange
    }

}
