package live.hms.roomkit.ui.meeting

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import live.hms.roomkit.R
import live.hms.roomkit.databinding.ActivityMeetingBinding
import live.hms.roomkit.ui.HMSPrebuiltOptions
import live.hms.roomkit.ui.settings.SettingsStore
import live.hms.roomkit.util.ROOM_CODE
import live.hms.roomkit.util.ROOM_PREBUILT
import live.hms.video.error.HMSException
import live.hms.video.sdk.HMSActionResultListener

class MeetingActivity : AppCompatActivity() {

  private var _binding: ActivityMeetingBinding? = null

  private val binding: ActivityMeetingBinding
    get() = _binding!!

  var settingsStore : SettingsStore? = null

  private val meetingViewModel: MeetingViewModel by viewModels {
    MeetingViewModelFactory(
      application,
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    _binding = ActivityMeetingBinding.inflate(layoutInflater)

    setContentView(binding.root)
    supportActionBar?.setDisplayShowTitleEnabled(false)
    settingsStore = SettingsStore(this)

    val hmsPrebuiltOption : HMSPrebuiltOptions? = intent!!.extras!![ROOM_PREBUILT] as? HMSPrebuiltOptions
    val roomCode : String = intent!!.getStringExtra(ROOM_CODE)!!

    //todo show a loader UI
    meetingViewModel.initSdk(roomCode, hmsPrebuiltOption, object : HMSActionResultListener {
      override fun onError(error: HMSException) {
          runOnUiThread {
            Toast.makeText(this@MeetingActivity, error.message, Toast.LENGTH_SHORT).show()
            finish()
            }
      }

      override fun onSuccess() {
        runOnUiThread {
          val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
          val navController = navHostFragment.navController
          val topFragment = navHostFragment.childFragmentManager.fragments.firstOrNull()
          if (settingsStore?.showPreviewBeforeJoin == true && (topFragment is MeetingFragment).not())
            navController?.setGraph(R.navigation.meeting_nav_graph, intent.extras)
          initViewModels()
        }
      }
    })

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  override fun onDestroy() {
    super.onDestroy()
    _binding = null
  }

  private fun initViewModels() {
    meetingViewModel.title.observe(this) {
    }
    meetingViewModel.isRecording.observe(this) {
      invalidateOptionsMenu()
    }
    meetingViewModel.pinnedTrack.observe(this) {
      if(it != null)
        Toast.makeText(this,"Spotlight: ${it.peer.name}", Toast.LENGTH_SHORT).show()
    }
  }
}
