package tech.muso.demo.rekoil

import kotlinx.android.synthetic.main.activity_main.*

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import tech.muso.demo.rekoil.ui.main.DemoPageAdapter

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val demoPageAdapter = DemoPageAdapter(this, supportFragmentManager)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        view_pager.adapter = demoPageAdapter
        view_pager.isUserInputEnabled = false

        // use mediator to connect viewpager and our empty TabLayout in XML
        val mediator: TabLayoutMediator = TabLayoutMediator(tabs, view_pager) { tab, position ->
            // onConfigureTab
            demoPageAdapter.link(tab, position)
        }.apply(TabLayoutMediator::attach) // attach() call once set up.
    }
}