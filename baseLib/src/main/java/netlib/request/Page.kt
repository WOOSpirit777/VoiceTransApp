package netlib.request

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Page(var page_size: Int = 20, var page_index: Int = 1) : Parcelable