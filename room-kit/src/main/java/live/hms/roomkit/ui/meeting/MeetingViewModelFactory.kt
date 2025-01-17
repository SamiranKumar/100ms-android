package live.hms.roomkit.ui.meeting

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MeetingViewModelFactory(
  private val application: Application
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(MeetingViewModel::class.java)) {
      return MeetingViewModel(application) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class $modelClass")
  }
}