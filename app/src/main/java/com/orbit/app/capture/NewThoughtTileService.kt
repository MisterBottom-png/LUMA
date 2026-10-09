package com.orbit.app.capture

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.orbit.app.MainActivity

/** Quick Settings tile: opens Home with the cursor in the capture box. Nothing else. */
class NewThoughtTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = NewThought.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

/** The "new thought" entry used by the launcher shortcut and the Quick Settings tile. */
object NewThought {
    const val Action = "com.orbit.app.action.NEW_THOUGHT"

    fun intent(context: android.content.Context): Intent =
        Intent(context, MainActivity::class.java).setAction(Action)

    fun isRequest(intent: Intent?): Boolean = intent?.action == Action
}
