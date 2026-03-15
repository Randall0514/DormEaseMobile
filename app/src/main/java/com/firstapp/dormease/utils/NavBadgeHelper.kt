package com.firstapp.dormease.utils

// FILE PATH: app/src/main/java/com/firstapp/dormease/utils/NavBadgeHelper.kt

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.firstapp.dormease.R

/**
 * Attaches a live unread-message badge to the Messages icon in any activity
 * that uses a bottom navigation bar with id [R.id.navMessages].
 *
 * Usage — in your Activity:
 *
 *   private val badgeHelper = NavBadgeHelper()
 *
 *   override fun onResume() {
 *       super.onResume()
 *       badgeHelper.attach(this)          // starts listening
 *   }
 *
 *   override fun onPause() {
 *       badgeHelper.detach()              // stops listening
 *       super.onPause()
 *   }
 *
 * The Messages nav item layout must contain:
 *   - android:id="@+id/navMessages"        (the parent LinearLayout)
 *   - android:id="@+id/tvNavMessagesBadge" (a TextView for the badge number)
 *
 * Add the badge TextView inside navMessages in your layout — see the example
 * snippet at the bottom of this file.
 */
class NavBadgeHelper {

    private var listener: ((Int) -> Unit)? = null

    fun attach(activity: Activity) {
        val badgeView = activity.findViewById<TextView?>(R.id.tvNavMessagesBadge)
            ?: return   // layout doesn't have a badge slot — nothing to do

        val listener: (Int) -> Unit = { total ->
            activity.runOnUiThread {
                if (total > 0) {
                    badgeView.text       = if (total > 99) "99+" else total.toString()
                    badgeView.visibility = View.VISIBLE
                } else {
                    badgeView.visibility = View.GONE
                }
            }
        }

        this.listener = listener
        UnreadMessageCounter.addListener(listener)

        // Apply current count immediately (badge was already non-zero before attach)
        listener(UnreadMessageCounter.total)
    }

    fun detach() {
        listener?.let { UnreadMessageCounter.removeListener(it) }
        listener = null
    }
}

/*
 ┌─────────────────────────────────────────────────────────────────────────┐
 │  LAYOUT SNIPPET — add tvNavMessagesBadge inside your navMessages item   │
 │                                                                         │
 │  Replace the plain navMessages LinearLayout in your bottom nav with:   │
 │                                                                         │
 │  <FrameLayout                                                           │
 │      android:id="@+id/navMessages"                                     │
 │      android:layout_width="0dp"                                        │
 │      android:layout_height="match_parent"                              │
 │      android:layout_weight="1">                                        │
 │                                                                         │
 │      <LinearLayout                                                      │
 │          android:layout_width="match_parent"                           │
 │          android:layout_height="match_parent"                          │
 │          android:orientation="vertical"                                │
 │          android:gravity="center">                                     │
 │          <ImageView ... ic_nav_messages />                              │
 │          <TextView ... "Messages" />                                   │
 │      </LinearLayout>                                                    │
 │                                                                         │
 │      <!-- Badge bubble -->                                              │
 │      <TextView                                                          │
 │          android:id="@+id/tvNavMessagesBadge"                          │
 │          android:layout_width="18dp"                                   │
 │          android:layout_height="18dp"                                  │
 │          android:layout_gravity="top|center_horizontal"               │
 │          android:layout_marginStart="14dp"                             │
 │          android:layout_marginTop="4dp"                                │
 │          android:gravity="center"                                      │
 │          android:textSize="10sp"                                       │
 │          android:textColor="#FFFFFF"                                   │
 │          android:textStyle="bold"                                      │
 │          android:background="@drawable/nav_message_badge"              │
 │          android:visibility="gone"                                     │
 │          android:text="0" />                                           │
 │                                                                         │
 │  </FrameLayout>                                                         │
 └─────────────────────────────────────────────────────────────────────────┘
*/