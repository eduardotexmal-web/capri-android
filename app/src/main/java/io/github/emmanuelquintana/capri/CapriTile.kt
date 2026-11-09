package io.github.emmanuelquintana.capri

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Ctrl+Alt+C, on a phone: a Quick Settings tile that puts her away or brings her back (or starts her). */
class CapriTile : TileService() {
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(Lang.wrap(newBase))

    override fun onStartListening() {
        qsTile?.apply {
            state = if (CapriService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (CapriService.running) CapriService.send(this, CapriService.ACTION_TOGGLE)
        else CapriService.start(this)
        onStartListening()
    }
}
