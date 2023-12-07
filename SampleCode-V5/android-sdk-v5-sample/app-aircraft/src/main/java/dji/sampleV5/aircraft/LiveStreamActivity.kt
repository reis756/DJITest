package dji.sampleV5.aircraft

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.fragment.app.commit
import dji.sampleV5.modulecommon.pages.DefaultLayoutTestFragment
import dji.sampleV5.modulecommon.pages.MultiVideoChannelFragment
import dji.sampleV5.modulecommon.pages.VideoChannelFragment

class LiveStreamActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_stream)

        supportFragmentManager.commit {
            replace(R.id.nav_host_fragment_content_main, DefaultLayoutTestFragment())
        }
    }
}