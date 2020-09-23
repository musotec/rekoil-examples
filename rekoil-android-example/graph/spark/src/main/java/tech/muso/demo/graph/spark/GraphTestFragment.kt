package tech.muso.demo.graph.spark

import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.RadioGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import kotlinx.coroutines.*
import tech.muso.demo.graph.spark.graph.Line
import tech.muso.demo.graph.spark.helpers.compose
import tech.muso.demo.graph.spark.helpers.generate
import tech.muso.rekoil.core.Atom
import tech.muso.rekoil.core.RekoilScope
import tech.muso.rekoil.core.Selector
import tech.muso.rekoil.core.launch
import java.lang.Math.*
import java.util.*

/**
 * Example Rekoil usage within an Android Fragment.
 *
 * The RekoilScope for the Fragment uses a combined CoroutineScope
 * of [lifecycleScope] and [Dispatchers.Main].
 *
 * This is to only compute during the life of the UI (Main),
 * and the lifecycle of this fragment.
 *
 * Because we are using [Dispatchers.Main] for the RekoilScope usage
 * within the [LineGraphView], we would face a race condition.
 * The [lifecycleScope] executes prior to [Dispatchers.Main] as
 * it is before views are created.
 *
 * If the fragment is swapped, then the [lifecycleScope] will handle
 * pausing of computations during the background state.
 * While Dispatchers.Main will remain active on another fragment.
 */
class GraphTestFragment : Fragment(), LifecycleOwner {

    private fun initializeGraph(lines: List<LineRekoilAdapter>, seed: Long = 6212812011245141258L) {
        val random = Random(seed)
        lines.forEachIndexed { i, line ->
            Log.w("INIT", "[$i] seed: $seed")
            var base: Float = random.nextInt(10000).toFloat()
            // generate a list of random numbers with of points 200/400 // TODO: fix comment
            val data = (0 until (400 / ((i % 3) + 1))).map {
                base += random.nextGaussian().toFloat() * ((i % 4) + 1)
                base
            }
            line.data.value = data.mapIndexed { index, y -> PointF(index.toFloat(), y) }
            line.scaleType.value = Line.ScaleMode.ALIGN_START
        }
    }

    private fun randomize(line: LineRekoilAdapter) {
        val random = Random()
        var base: Float = random.nextInt(10000).toFloat()
        val factor = 1 + random.nextInt(9)
        val data = (0 until 400).map {
            base += random.nextGaussian().toFloat() * factor
            base
        }
        line.data.value = data.mapIndexed { index, y -> PointF(index.toFloat(), y) }
    }

    // Create a rekoil scope that lives only on the Fragment Lifecycle to avoid extraneous work.
    private val rekoilScope: RekoilScope
            = RekoilScope(lifecycleScope + Dispatchers.Main)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.graph_testing, container, false).also { rootView ->

            var inScrubbing = false
            val graphView = rootView.findViewById<LineGraphView>(R.id.sparkLineGraph)
            val graphScope = graphView.graphRekoilScope

            val radioGroup = rootView.findViewById<RadioGroup>(R.id.scale_type_radio_group)

            val radioFit = rootView.findViewById<MaterialRadioButton>(R.id.radioButton1)
            val radioGlobal = rootView.findViewById<MaterialRadioButton>(R.id.radioButton2)
            val radioAlign = rootView.findViewById<MaterialRadioButton>(R.id.radioButton3)

            val buttonRandomize = rootView.findViewById<Button>(R.id.randomize_button)
            val switchConnectPoints = rootView.findViewById<SwitchMaterial>(R.id.switch_connect_points)

            fun updateUiState(selectedLine: LineRekoilAdapter) {
                // detach listeners
                switchConnectPoints.setOnCheckedChangeListener(null)

                // update ui state
                selectedLine.scaleType.apply {
                    // remove existing listener.
                    radioGroup.setOnCheckedChangeListener(null)
                    // update radio buttons for new line status.
                    when (value) {
                        Line.ScaleMode.FIT -> radioFit.isChecked = true
                        Line.ScaleMode.GLOBAL -> radioGlobal.isChecked = true
                        Line.ScaleMode.ALIGN_START -> radioAlign.isChecked = true
                        else -> {}
                    }
                }

                // update connected status
                switchConnectPoints.isChecked = selectedLine.connectPoints.value

                // reattach listeners
                switchConnectPoints.setOnCheckedChangeListener { _, isChecked ->
                    selectedLine.connectPoints.value = isChecked
                    selectedLine.redraw()
                }
            }

            radioFit.text = "Fit"
            radioGlobal.text = "Global"
            radioAlign.text = "Align"

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
                ) : OnTabSelectedListener {

                    override fun onTabSelected(tab: TabLayout.Tab) {
                        // update our atom with the correct position
                        selectedIndexAtom.value = tab.position
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab?) {}    // No-op
                    override fun onTabReselected(tab: TabLayout.Tab?) {}    // No-op
                }

                val currentLineAdapter: Selector<LineRekoilAdapter?> = selector {
                    // if index or line array changes, then update our current line.
                    val currentIndex = get(selectedLineIndex)
                    val lineList = get(lines)!!
                    return@selector lineList[currentIndex]  // return adapter at index.
                }

                buttonRandomize.setOnClickListener {
                    currentLineAdapter.value?.let { randomize(it) }
                }

                val currentLine: Selector<Line?> = selector {
                    // get the line of the adapter whenever it changes.
                    get(currentLineAdapter)?.line
                }

                // save the last line whenever we change the current line to reset properties
                var lastLine: Line? = null
                selector {
                    lastLine?.lineWidth = 4f
                    // with current line, update thickness and last value
                    with(get(currentLine)) {
                        this?.lineWidth = 8f
                        lastLine = this
                    }
                } // because line selection is a property of this fragment,
                // we are not adding a selector/atom to the line itself to control this.

                // selector to perform changes whenever line adapter changes.
                selector {
                    val adapter = get(currentLineAdapter)

                    adapter?.let { updateUiState(it) }

                    // update for new scale type
                    adapter?.scaleType?.apply {
                        // remove existing listener.
                        radioGroup.setOnCheckedChangeListener(null)
                        // update radio buttons for new line status.
                        when (value) {
                            Line.ScaleMode.FIT -> radioFit.isChecked = true
                            Line.ScaleMode.GLOBAL -> radioGlobal.isChecked = true
                            Line.ScaleMode.ALIGN_START -> radioAlign.isChecked = true
                            else -> { }
                        }
                        // attach new listener to receive updates.
                        radioGroup.setOnCheckedChangeListener { group, checkedId ->
                            when (checkedId) {
                                R.id.radioButton1 -> {
                                    if (radioFit.isChecked)
                                        value = Line.ScaleMode.FIT
                                }
                                R.id.radioButton2 -> {
                                    if (radioGlobal.isChecked)
                                        value = Line.ScaleMode.GLOBAL
                                }
                                R.id.radioButton3 -> {
                                    if (radioAlign.isChecked)
                                        value = Line.ScaleMode.ALIGN_START
                                }
                            }
                        }
                    }
                }

                // Attachment of the tabs
                val tabListener =
                    LinesOnTabSelectedListener(selectedLineIndex)

                val tabs = rootView.findViewById<TabLayout>(R.id.tabs).apply {
                    addOnTabSelectedListener(tabListener)
                }

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
                        }.also { tabSubscribers.add(it) }   // and also add it to the list.
                    }
                }
            }

            // create a selector that watches the lines and initializes the graph
            rekoilScope.selector {
                // when we update our selector, initialize the graph (add data)
                val lineList = get(lines)
                if (lineList != null) initializeGraph(lineList)
            }

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

                for (i in 0..6) {
                    val color =
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
                    this.add(Line.Builder().setColor(color))
                        .apply {
                            // set identifier
                            identifier = i
                            // start demo coroutine
                            if (identifier == 3) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    delay(5000)
                                    data = (0..500).mapIndexed { index, value ->
                                        PointF(
                                            index.toFloat(),
                                            9040.toFloat() + value
                                        )
                                    }
                                }
                            } else {
                                CoroutineScope(Dispatchers.IO).launch {
                                    delay(1000)
                                    when (identifier) {
                                        0 -> data = (0..360).generate(
                                            compose(::sin, ::toRadians)
                                        ).mapIndexed { i, y -> PointF(i.toFloat(), 500f * y) }
                                        1 -> data = (0..360).generate(
                                            compose(::cos, ::toRadians)
                                        ).mapIndexed { i, y -> PointF(i.toFloat(), 250f * y)}
//                                        2 -> data = (0..360).generate(
//                                            compose(compose(::sin, ::toRadians), ::cos)
//                                        ).mapIndexed { i, y -> PointF(i.toFloat(), 250f * y)}
                                    }
                                }
                            }
                        }
                }

                invalidate()
            }

            rootView.findViewById<MaterialButton>(R.id.reset_button).setOnClickListener {
                lines.value?.let { lines -> initializeGraph(lines) }
            }
        }
    }
}