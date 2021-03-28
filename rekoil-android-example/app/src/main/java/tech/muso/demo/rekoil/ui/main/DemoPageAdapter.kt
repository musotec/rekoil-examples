package tech.muso.demo.rekoil.ui.main

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import tech.muso.demo.rekoil.R
import tech.muso.demo.graph.spark.GraphTestFragment
import tech.muso.demo.rekoil.GraphStockFragment
import tech.muso.demo.theme.ThemeTestFragment

class DemoPageAdapter(private val activity: FragmentActivity, fm: FragmentManager)
    : FragmentStateAdapter(fm, activity.lifecycle) {

    init {
        fm.fragmentFactory = FragmentFactory()
    }

    /**
     * Link the tab to the position by querying the current list of fragments.
     */
    fun link(tab: TabLayout.Tab, position: Int) {
        getInstance()[position].apply {
            this.link(activity, tab)
        }
    }

    /**
     * Represent our fragment pages to give them icons or text and link it all up cleanly.
     */
    internal data class FragmentPage(
        val fragment: Fragment,
        @DrawableRes val drawableRes: Int
    ) {
        fun link(context: Context, tab: TabLayout.Tab) {
            // set the icon for the tab
            context.getDrawable(drawableRes)?.let {
                tab.icon = it
            }
        }
    }

    override fun createFragment(position: Int): Fragment = getInstance()[position].fragment
    override fun getItemCount(): Int = getInstance().size

    /**
     * Companion object as a helper for the SectionsPageAdapter class.
     *
     * This makes a Singleton of the pages in the adapter that is tied to the adapter object.
     */
    companion object {
        private var pagesInstance: List<FragmentPage>? = null

        private fun generateFragmentsList(): List<FragmentPage> {
            return ArrayList<FragmentPage>().apply {
                add(
                    FragmentPage(
                        GraphTestFragment(),
                        R.drawable.ic_mtrl_chip_checked_diamond)
                )
                add(
                    FragmentPage(
                        GraphStockFragment(),
                        R.drawable.ic_baseline_trending_up_24)
                )
                add(
                    FragmentPage(
                        ThemeTestFragment(),
                        R.drawable.ic_android_color_control_normal_24dp)
                )
            }
        }

        internal fun getInstance(): List<FragmentPage> {
            return pagesInstance ?: generateFragmentsList()
                .also { generatedFragments ->
                    pagesInstance = generatedFragments
                }
        }
    }
}