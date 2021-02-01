package live.hms.android100ms.ui.meeting

import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.brytecam.lib.*
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import live.hms.android100ms.R
import live.hms.android100ms.databinding.FragmentMeetingBinding
import live.hms.android100ms.model.RoomDetails
import live.hms.android100ms.ui.home.HomeActivity
import live.hms.android100ms.ui.home.settings.SettingsStore
import live.hms.android100ms.ui.meeting.chat.ChatMessage
import live.hms.android100ms.ui.meeting.chat.ChatViewModel
import live.hms.android100ms.ui.meeting.videogrid.VideoGridAdapter
import live.hms.android100ms.util.*
import live.hms.android100ms.audio.HMSAudioManager
import java.util.*


class MeetingFragment : Fragment() {

  companion object {
    private const val TAG = "MeetingFragment"
  }

  private var binding by viewLifecycle<FragmentMeetingBinding>()

  private lateinit var settings: SettingsStore
  private lateinit var roomDetails: RoomDetails

  private var isAudioMuted = false

  private val chatViewModel: ChatViewModel by activityViewModels()

  private val meetingViewModel: MeetingViewModel by viewModels {
    MeetingViewModelFactory(
      requireActivity().application,
      requireActivity().intent!!.extras!![ROOM_DETAILS] as RoomDetails
    )
  }

  private lateinit var audioManager: HMSAudioManager
  private lateinit var clipboard: ClipboardManager

  override fun onResume() {
    super.onResume()
    audioManager.updateAudioDeviceState()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    roomDetails = requireActivity().intent!!.extras!![ROOM_DETAILS] as RoomDetails

    clipboard = requireActivity()
      .getSystemService(Context.CLIPBOARD_SERVICE)
        as ClipboardManager
    audioManager = HMSAudioManager.create(requireContext())
  }

  override fun onStop() {
    super.onStop()
    stopAudioManager()
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.action_share_link -> {
        val meetingUrl = roomDetails.let {
          "https://${it.env}.100ms.live/?room=${it.roomId}&env=${it.env}&role=Guest"
        }
        val sendIntent = Intent().apply {
          action = Intent.ACTION_SEND
          putExtra(Intent.EXTRA_TEXT, meetingUrl)
          type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
      }
      R.id.action_record_meeting -> {
        Toast.makeText(requireContext(), "Recording Not Supported", Toast.LENGTH_SHORT).show()
      }
      R.id.action_share_screen -> {
        Toast.makeText(requireContext(), "Screen Share Not Supported", Toast.LENGTH_SHORT).show()
      }
      R.id.action_email_logs -> {
        requireContext().startActivity(
          EmailUtils.getCrashLogIntent(requireContext())
        )
      }
    }
    return false
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    super.onCreateOptionsMenu(menu, inflater)
    menu.findItem(R.id.action_volume).apply {
      setOnMenuItemClickListener {
        isAudioMuted = !isAudioMuted
        if (isAudioMuted) {
          setIcon(R.drawable.ic_baseline_volume_off_24)
        } else {
          setIcon(R.drawable.ic_baseline_volume_up_24)
        }

        meetingViewModel.toggleSpeakerAudio(isAudioMuted)

        true
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    initViewModel()
    setHasOptionsMenu(true)
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = FragmentMeetingBinding.inflate(inflater, container, false)
    settings = SettingsStore(requireContext())

    initVideoGrid()
    initButtons()
    initOnBackPress()

    meetingViewModel.startMeeting()
    return binding.root
  }

  private fun goToHomePage() {
    Intent(requireContext(), HomeActivity::class.java).apply {
      Log.v(TAG, "MeetingActivity.finish() -> going to HomeActivity :: $this")
      startActivity(this)
    }

    requireActivity().finish()
  }

  private fun initViewModel() {
    chatViewModel.setSendBroadcastCallback { meetingViewModel.broadcastMessage(it) }

    meetingViewModel.state.observe(viewLifecycleOwner) { state ->
      when (state) {
        is MeetingState.Disconnected -> {
          if (state.showDialog) {
            AlertDialog.Builder(requireContext())
              .setMessage(state.message)
              .setTitle(state.heading)
              .setPositiveButton(R.string.leave) { dialog, _ ->
                Log.d(TAG, "Leaving meeting due to '${state.heading}' :: ${state.message}")

                if (state.goToHome) {
                  goToHomePage()
                } else {
                  meetingViewModel.startMeeting()
                }

                dialog.dismiss()
              }
              .setCancelable(false)
              .create()
              .show()
          } else if (state.goToHome) {
            goToHomePage()
          }
        }
        is MeetingState.Connecting -> {
          updateProgressBarUI(state.heading, state.message)
          showProgressBar()
        }
        is MeetingState.Joining -> {
          updateProgressBarUI(state.heading, state.message)
          showProgressBar()
        }
        is MeetingState.LoadingMedia -> {
          updateProgressBarUI(state.heading, state.message)
          showProgressBar()
        }
        is MeetingState.PublishingMedia -> {
          updateProgressBarUI(state.heading, state.message)
          showProgressBar()
        }
        is MeetingState.Ongoing -> {
          hideProgressBar()
        }
        is MeetingState.Disconnecting -> {
          updateProgressBarUI(state.heading, state.message)
          showProgressBar()
        }
      }
    }

    meetingViewModel.isVideoEnabled.observe(viewLifecycleOwner) { enabled ->
      binding.buttonToggleVideo.apply {
        visibility = if (settings.publishVideo) View.VISIBLE else View.GONE
        isEnabled = settings.publishVideo

        setIconResource(
          if (enabled) R.drawable.ic_baseline_videocam_24
          else R.drawable.ic_baseline_videocam_off_24
        )
      }
    }

    meetingViewModel.isAudioEnabled.observe(viewLifecycleOwner) { enabled ->
      binding.buttonToggleAudio.apply {
        visibility = if (settings.publishAudio) View.VISIBLE else View.GONE
        isEnabled = settings.publishAudio

        setIconResource(
          if (enabled) R.drawable.ic_baseline_mic_24
          else R.drawable.ic_baseline_mic_off_24
        )
      }
    }

    meetingViewModel.tracks.observe(viewLifecycleOwner) { tracks ->
      // TODO: This will fire whenever the onResume is called.

      val adapter = binding.viewPagerVideoGrid.adapter as VideoGridAdapter
      adapter.setItems(tracks)
      Log.v(TAG, "updated video Grid UI with ${tracks.size} items")
    }

    meetingViewModel.broadcastsReceived.observe(viewLifecycleOwner) { data ->
      chatViewModel.receivedMessage(
        ChatMessage(
          data.peer.uid,
          data.senderName,
          Date(),
          data.msg,
          false
        )
      )
    }
  }

  private fun startAudioManager() {
    crashlyticsLog(TAG, "Starting Audio manager")

    audioManager.start { selectedAudioDevice, availableAudioDevices ->
      crashlyticsLog(
        TAG,
        "onAudioManagerDevicesChanged: $availableAudioDevices, selected: $selectedAudioDevice"
      )
    }
  }

  private fun stopAudioManager() {
    val devices = audioManager.selectedAudioDevice
    crashlyticsLog(TAG, "Stopping Audio Manager:selectedAudioDevice:${devices}")
    audioManager.stop()
  }

  private fun initVideoGrid() {
    binding.viewPagerVideoGrid.apply {
      offscreenPageLimit = 1
      adapter = VideoGridAdapter(this@MeetingFragment) { video ->
        // TODO: Implement Hero/Pin View

        Log.v(TAG, "onVideoItemClick: $video")

        Snackbar.make(
          binding.root,
          "Name: ${video.peer.userName} (${video.peer.role}) \nId: ${video.peer.customerUserId}",
          Snackbar.LENGTH_LONG,
        ).setAction("Copy") {
          val clip = ClipData.newPlainText("Customer Id", video.peer.customerUserId)
          clipboard.setPrimaryClip(clip)
          Toast.makeText(
            requireContext(),
            "Copied customer id of ${video.peer.userName} to clipboard",
            Toast.LENGTH_SHORT
          ).show()
        }.show()
      }

      TabLayoutMediator(binding.tabLayoutDots, this) { _, _ ->
        // No text to be shown
      }.attach()
    }

  }

  private fun updateProgressBarUI(heading: String, description: String = "") {
    binding.progressBar.heading.text = heading
    binding.progressBar.description.apply {
      visibility = if (description.isEmpty()) View.GONE else View.VISIBLE
      text = description
    }
  }

  private fun hideProgressBar() {
    binding.viewPagerVideoGrid.visibility = View.VISIBLE
    binding.tabLayoutDots.visibility = View.VISIBLE
    binding.bottomControls.visibility = View.VISIBLE

    binding.progressBar.root.visibility = View.GONE
  }

  private fun showProgressBar() {
    binding.viewPagerVideoGrid.visibility = View.GONE
    binding.tabLayoutDots.visibility = View.GONE
    binding.bottomControls.visibility = View.GONE

    binding.progressBar.root.visibility = View.VISIBLE
  }

  private fun initButtons() {
    binding.buttonToggleVideo.setOnSingleClickListener(200L) {
      Log.v(TAG, "buttonToggleVideo.onClick()")
      meetingViewModel.toggleUserMic()
    }

    binding.buttonToggleAudio.setOnSingleClickListener(200L) {
      Log.v(TAG, "buttonToggleAudio.onClick()")
      meetingViewModel.toggleUserVideo()
    }

    binding.buttonOpenChat.setOnClickListener {
      findNavController().navigate(
        MeetingFragmentDirections.actionMeetingFragmentToChatBottomSheetFragment(
          roomDetails,
          meetingViewModel.peer.customerUserId
        )
      )
    }

    binding.buttonEndCall.setOnSingleClickListener(350L) { meetingViewModel.leaveMeeting() }

    binding.buttonFlipCamera.setOnClickListener {
      meetingViewModel.flipCamera()
    }
  }

  private fun cleanup() {
    // Because the scope of Chat View Model is the entire activity
    // We need to perform a cleanup
    chatViewModel.clearMessages()

    stopAudioManager()
    crashlyticsLog(TAG, "cleanup() done")
  }

  private fun initOnBackPress() {
    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          Log.v(TAG, "initOnBackPress -> handleOnBackPressed")
          meetingViewModel.leaveMeeting()
        }
      })
  }
}