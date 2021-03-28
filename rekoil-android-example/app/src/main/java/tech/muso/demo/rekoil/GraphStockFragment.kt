package tech.muso.demo.rekoil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import tech.muso.demo.graph.core.CandleGraphable
import tech.muso.demo.graph.spark.LineGraphView
import tech.muso.demo.graph.spark.R
import tech.muso.demo.graph.spark.graph.Line
import tech.muso.rekoil.core.Atom
import tech.muso.rekoil.core.RekoilScope
import tech.muso.rekoil.core.launch

class GraphStockFragment : Fragment(), LifecycleOwner {

    // Create a rekoil scope that lives only on the Fragment Lifecycle to avoid extraneous work.
    private val rekoilScope: RekoilScope
            = RekoilScope(lifecycleScope + Dispatchers.Main)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.graph_testing, container, false).also { rootView ->

            val graphView = rootView.findViewById<LineGraphView>(R.id.sparkLineGraph)

            // create a selector for the current lines on the graph from the graph view's scope.
            val lines = rekoilScope.withScope(graphView.graphRekoilScope) {
                get(graphView.lines)
            }

            rekoilScope.launch {

                // create a line selection atom
                val selectedLineIndex: Atom<Int> = atom { 0 }

                // and an OnTabSelectedListener to serve as an adapter
                class LinesOnTabSelectedListener(
                    val selectedIndexAtom: Atom<Int>
                ) : TabLayout.OnTabSelectedListener {

                    override fun onTabSelected(tab: TabLayout.Tab) {
                        // update our atom with the correct position
                        selectedIndexAtom.value = tab.position
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab?) {}    // No-op
                    override fun onTabReselected(tab: TabLayout.Tab?) {}    // No-op
                }

                // Attachment of the tabs
                val tabListener = LinesOnTabSelectedListener(selectedLineIndex)
                val tabs = rootView.findViewById<TabLayout>(R.id.tabs).apply {
                    addOnTabSelectedListener(tabListener)
                }

                // create a selector that watches the lines and initializes the graph
                rekoilScope.selector {
                    // when we update our selector, initialize the graph (add data)
                    val lineList = get(lines)
//                    if (lineList != null) initializeGraph(lineList)
                }

//                val currentLineAdapter: Selector<LineRekoilAdapter?> = selector {
//                    // if index or line array changes, then update our current line
//                    val currentIndex = get(selectedLineIndex)
//                    val lineList = get(lines)!!
//                    return@selector lineList[currentIndex]  // return adapter at index.
//                }

                // create a list of subscribers for each line color,
                // since we will want an easy way to remove them as the tabs are cleared.
                val tabSubscribers = mutableListOf<Job>()
                withScope(graphView.graphRekoilScope) {
                    // otherwise we create many selectors with no way to release when list changes
                    val lineList = get(graphView.lines)

                    // clear all tabs
                    tabs.removeAllTabs()
                    // cancel subscribers for any old tabs
                    tabSubscribers.forEach { it.cancel() }
                    tabSubscribers.clear()  // and clear the list.
                    // now iterate over new list of lines, and create the tab and subscriber
                    lineList.forEachIndexed { position, adapter ->
                        val newTab = tabs.newTab()
                        newTab.icon = context?.getDrawable(R.drawable.ic_baseline_trending_up_24)
                        tabs.addTab(newTab)
                        // create the subscription to the line color
                        adapter.line.lineColorAtom.subscribe { color ->
                            // and apply color filter to the tab icon whenever the color changes
                            newTab.icon?.colorFilter =
                                BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                                    color, BlendModeCompat.SRC_IN
                                )
                        }//.also { tabSubscribers.add(it) }   // and also add it to the list.
                    }
                }

                var inScrubbing = false
                // initialize graph with a few lines and touch listeners.
                graphView.apply {
                    this.clearLines()   // FIXME: extraneous call
                    this.scrubListener = object : LineGraphView.ScrubStateListener {
                        override fun onRangeSelected() {}
                        override fun onScrubStateChanged(isScrubbing: Boolean) {
                            inScrubbing = isScrubbing
                        }
                    }


                    this.bgColor = ContextCompat.getColor(context, R.color.colorBackgroundBlue)
                    fun color(i: Int) =
                        when (i) {
                            0 -> ContextCompat.getColor(context, R.color.colorAccentRed)
                            1 -> ContextCompat.getColor(context, R.color.colorAccentOrange)
                            2 -> ContextCompat.getColor(context, R.color.colorAccentYellow)
                            3 -> ContextCompat.getColor(context, R.color.colorAccentGreen)
                            4 -> ContextCompat.getColor(context, R.color.colorAccentTeal)
                            5 -> ContextCompat.getColor(context, R.color.colorAccentCyan)
                            6 -> ContextCompat.getColor(context, R.color.colorAccentBlue)
                            else -> ContextCompat.getColor(context, R.color.colorAccentOrange)
                        }

//                    this.add(Line.Builder().setColor(color(1))).apply {
//                        adapter.data.value = (0..360 step 2).generate(
//                            compose(Math::sin, Math::toRadians)
//                        ).mapIndexed { i, y ->
//                            PointGraphable(
//                                i.toFloat(),
//                                500f * y
//                            )
////                                            val start = 500f*y
////                                            CandleGraphable(
////                                                x = i.toFloat(),
////                                                open = start,
////                                                close = start + 50,
////                                                low = start-50,
////                                                high = start + 100,
////                                                volume = 100
////                                            )
//                        }
//                    }
                    this.add(Line.Builder().setColor(color(2)))
                        .also { line ->
                            // set identifier
                            line.identifier = "SPY"
//                            val mutableList = mutableListOf<CandleGraphable>(CandleGraphable.EMPTY_CANDLE)
//                            val mutableList = LinkedList<CandleGraphable>()
                            // start demo coroutine
                            CoroutineScope(Dispatchers.IO).launch {
                                subscribe("SPY").collect { json ->
                                    val bars = Bars.fromJson(json)
                                    if (bars != null) {
                                        val queue = line.adapter.data
//                                        println("Bars[${queue.size}: ${bars.bars}")
                                        val newCandle = bars["SPY"]!!.map { candle ->
                                            CandleGraphable(
                                                x = candle.timeSeconds.toFloat(),
                                                open = candle.open.toFloat(),
                                                close = candle.close.toFloat(),
                                                high = candle.high.toFloat(),
                                                low = candle.low.toFloat(),
                                                volume = candle.volume
                                            )
                                        }[0]

//                                        println("--> [${queue.size}] $newCandle")


                                        // add new candle then invalidate
                                        queue.add(newCandle)

//                                        line.adapter.data.invalidate()
                                        //.value = mutableList.toList()
//                                        line.adapter.scaleType.value = Line.ScaleMode.ALIGN_START
                                    }
                                }
                            }
                        }


                }
            }

        }
    }

}