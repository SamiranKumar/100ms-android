package live.hms.roomkit.ui.meeting.pinnedvideo

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import live.hms.roomkit.databinding.FragmentPinnedVideoBinding
import live.hms.roomkit.ui.meeting.CustomPeerMetadata
import live.hms.roomkit.ui.meeting.MeetingTrack
import live.hms.roomkit.ui.meeting.MeetingViewModel
import live.hms.roomkit.ui.meeting.MeetingViewModelFactory
import live.hms.roomkit.ui.settings.SettingsStore
import live.hms.roomkit.util.*
import live.hms.video.sdk.models.enums.HMSPeerUpdate
import live.hms.videoview.HMSVideoView
import org.webrtc.RendererCommon

class PinnedVideoFragment : Fragment() {

  companion object {
    private const val TAG = "PinnedVideoFragment"
  }

  private var pinnedTrack: MeetingTrack? = null
  protected val settings: SettingsStore by lazy { SettingsStore(requireContext()) }

  private var binding by viewLifecycle<FragmentPinnedVideoBinding>()

  private val meetingViewModel: MeetingViewModel by activityViewModels {
    MeetingViewModelFactory(
      requireActivity().application
    )
  }

  private val videoListAdapter by lazy {
    VideoListAdapter(
      { track -> meetingViewModel.localPinnedTrack.postValue(track) },
      meetingViewModel.getStats(),
      settings.showStats
    )
  }

  // Determined using the onResume() and onPause()
  private var isViewVisible = false

  override fun onResume() {
    super.onResume()
    Log.d(TAG, "onResume()")

    isViewVisible = true
    handleOnPinVideoVisibilityChange()

    binding.recyclerViewVideos.adapter = videoListAdapter
  }

  override fun onPause() {

    Log.d(TAG, "onPause()")

    isViewVisible = false
    handleOnPinVideoVisibilityChange()

    // Detaching the recycler view adapter calls [RecyclerView.Adapter::onViewDetachedFromWindow]
    // which performs the required cleanup of the ViewHolder (Releases SurfaceViewRenderer Egl.Context)
    binding.recyclerViewVideos.adapter = null
    super.onPause()
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    Log.d(TAG, "onCreateView($inflater, $container, $savedInstanceState)")
    binding = FragmentPinnedVideoBinding.inflate(inflater, container, false)
    initRecyclerView()
    initPinnedView()
    initViewModels()
    return binding.root
  }

  private fun initPinnedView() {
    binding.pinVideo.hmsVideoView.apply {
      setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
      disableAutoSimulcastLayerSelect(meetingViewModel.isAutoSimulcastEnabled())
    }

    updatePinnedVideoText()
  }

  private fun initRecyclerView() {
    val orientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      LinearLayoutManager.VERTICAL
    } else {
      LinearLayoutManager.HORIZONTAL
    }
    binding.recyclerViewVideos.apply {
      layoutManager = LinearLayoutManager(requireContext(), orientation, false)
    }
  }

  private fun updatePinnedVideoText() {
    val nameStr = pinnedTrack?.peer?.name ?: "--"
    val isScreen = pinnedTrack?.isScreen ?: false
    binding.pinVideo.apply {
      name.text = nameStr
      nameInitials.text = NameUtils.getInitials(nameStr)
      iconScreenShare.visibility = if (isScreen) View.VISIBLE else View.GONE
    }
  }

  private fun handleOnPinVideoVisibilityChange() {
    pinnedTrack?.let { track : MeetingTrack ->
      binding.pinVideo.hmsVideoView.apply {
        if (isViewVisible) {
          track.video?.let {
            addTrack(it)
            visibility = if (track.video?.isDegraded == true) View.INVISIBLE else View.VISIBLE
          }

        } else {
          removeTrack()
          visibility = View.GONE
        }
      }
    }
  }

  @MainThread
  private fun changePinViewVideo(track: MeetingTrack) {
    binding.pinVideo.iconAudioOff.visibility = visibility(track.peer.audioTrack?.isMute == true)



    binding.pinVideo.iconAudioOff.visibility = visibility(track.peer.audioTrack?.isMute == true)
    if (track == pinnedTrack) {
      return
    }

    videoListAdapter.updatePinnedVideo(track)


    val view = HMSVideoView(requireContext())

    view.apply {
      disableAutoSimulcastLayerSelect(meetingViewModel.isAutoSimulcastEnabled())
      binding.pinVideo.surfaceViewHolder.forEach {
        if (it is HMSVideoView){
          it.removeTrack()
        }
      }
      binding.pinVideo.surfaceViewHolder.removeAllViews()
      binding.pinVideo.surfaceViewHolder.addView(this)
      visibility = View.GONE
      track.video?.let { videoTrack ->
        visibility = if (track.video?.isDegraded == true) View.INVISIBLE else View.VISIBLE
        view.addTrack(videoTrack)
      }

      }

    pinnedTrack = track
    updatePinnedVideoText()
    changePinnedRaiseHandState()


  }

  private fun initViewModels() {
    meetingViewModel.tracks.observe(viewLifecycleOwner) { tracks ->
      if(meetingViewModel.pinnedTrackUiUseCase.value == null){


      var toPin : MeetingTrack? = null
      if (tracks.isNotEmpty()) {
        // Pin a screen if possible else pin user's video
         toPin = tracks.find { it.isScreen } ?: tracks[0]

      }
        toPin?.let {
          changePinViewVideo(it)
        }
      }

      videoListAdapter.updateTotalSource(tracks)
      videoListAdapter.setItems(pinnedTrack)

      Log.d(TAG, "Updated video-list items: size=${tracks.size}")
    }

    meetingViewModel.pinnedTrackUiUseCase.observe(viewLifecycleOwner) {
      it?.let {
        if (pinnedTrack?.isScreen != true) {
          changePinViewVideo(it)
        }
      }
    }

    // This will change the raised hand state if the person does it while in this view.
    meetingViewModel.peerMetadataNameUpdate.observe(viewLifecycleOwner) { metadataNameChangedPeer ->
      // Check if it's the pinned video's hand raised.
      if (metadataNameChangedPeer.first.peerID == pinnedTrack?.peer?.peerID) {
        when (metadataNameChangedPeer.second) {
          HMSPeerUpdate.METADATA_CHANGED -> changePinnedRaiseHandState()
          HMSPeerUpdate.NAME_CHANGED -> changePinnedName()
        }
      }
      // Since the pinned person's video can also appear in the sublist, this has to be checked too
      videoListAdapter.itemChanged(metadataNameChangedPeer)

    }
  }

  private fun changePinnedRaiseHandState() {
    val customData = CustomPeerMetadata.fromJson(pinnedTrack?.peer?.metadata)
    if (customData != null) {
      binding.pinVideo.raisedHand.alpha = visibilityOpacity(customData.isHandRaised)
    }
  }

  private fun changePinnedName() {
    val newName = pinnedTrack?.peer?.name
    if (newName != null) {
      with(binding.pinVideo) {
        name.text = newName
        nameInitials.text = NameUtils.getInitials(newName)
      }
    }
  }
}