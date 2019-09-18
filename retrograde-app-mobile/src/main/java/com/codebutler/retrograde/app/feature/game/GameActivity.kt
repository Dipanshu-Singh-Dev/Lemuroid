/*
 * GameActivity.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.codebutler.retrograde.app.feature.game

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.codebutler.retrograde.BuildConfig
import com.codebutler.retrograde.common.kotlin.bindView
import com.codebutler.retrograde.lib.android.RetrogradeActivity
import com.codebutler.retrograde.lib.game.GameLoader
import com.codebutler.retrograde.lib.game.audio.GameAudio
import com.codebutler.retrograde.lib.game.display.GameDisplay
import com.codebutler.retrograde.lib.game.display.gl.GlGameDisplay
import com.codebutler.retrograde.lib.game.display.sw.SwGameDisplay
import com.codebutler.retrograde.lib.game.input.GameInput
import com.codebutler.retrograde.lib.library.db.entity.Game
import com.codebutler.retrograde.lib.retro.RetroDroid
import com.codebutler.retrograde.lib.util.subscribeBy
import com.gojuno.koptional.None
import com.gojuno.koptional.Some
import com.gojuno.koptional.toOptional
import com.swordfish.touchinput.pads.GamePadFactory
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject
import android.os.Vibrator
import com.codebutler.retrograde.R


class GameActivity : RetrogradeActivity() {
    companion object {
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_SAVE_FILE = "save_file"
    }

    @Inject lateinit var gameLoader: GameLoader

    private val progressBar by bindView<ProgressBar>(R.id.progress)
    private val gameDisplayLayout by bindView<FrameLayout>(R.id.game_display_layout)

    private lateinit var gameDisplay: GameDisplay
    private lateinit var gameInput: GameInput

    private var game: Game? = null
    private var retroDroid: RetroDroid? = null

    private var displayTouchInput: Boolean = false

    private var hapticFeedbackStrength = 15L
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        enableImmersiveMode()
        initVibrator()

        // TODO FILIPPO... There is a lot of duplication with tv GameActivity

        PreferenceManager.getDefaultSharedPreferences(this).apply {
            hapticFeedbackStrength = getInt(getString(R.string.pref_haptic_feedback_strength), 15).toLong()
        }

        val enableOpengl = true
        displayTouchInput = true

        gameDisplay = if (enableOpengl) {
            GlGameDisplay(this)
        } else {
            SwGameDisplay(this)
        }

        gameInput = GameInput(this)

        gameDisplayLayout.addView(gameDisplay.view, MATCH_PARENT, MATCH_PARENT)
        lifecycle.addObserver(gameDisplay)

        // FIXME: Full Activity lifecycle handling.
        if (savedInstanceState != null) {
            return
        }

        val gameId = intent.getIntExtra(EXTRA_GAME_ID, -1)
        gameLoader.load(gameId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .autoDisposable(scope())
                .subscribe(
                        { data ->
                            progressBar.visibility = View.GONE
                            loadRetro(data)
                        },
                        { error ->
                            Timber.e(error, "Failed to load game")
                            finish()
                        })

        if (BuildConfig.DEBUG) {
            addFpsView()
        }
    }

    private fun enableImmersiveMode() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun initVibrator() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun initTouchInputs(game: Game) {
        val frameLayout = findViewById<FrameLayout>(R.id.game_layout)

        val gameView = when (game.systemId) {
            in listOf("snes", "gba") -> GamePadFactory.getGamePadView(this, GamePadFactory.Layout.SNES)
            in listOf("nes", "gb", "gbc") -> GamePadFactory.getGamePadView(this, GamePadFactory.Layout.NES)
            in listOf("md") -> GamePadFactory.getGamePadView(this, GamePadFactory.Layout.GENESIS)
            else -> null
        }

        if (gameView != null) {
            frameLayout.addView(gameView)

            gameView.getEvents()
                    .doOnNext {
                        if (it.action == KeyEvent.ACTION_DOWN) {
                            performHapticFeedback()
                        }
                    }.autoDisposable(scope())
                    .subscribe { gameInput.onKeyEvent(KeyEvent(it.action, it.keycode)) }
        }
    }

    private fun performHapticFeedback() {
        vibrator.vibrate(hapticFeedbackStrength)
    }

    override fun onDestroy() {
        super.onDestroy()
        // This activity runs in its own process which should not live beyond the activity lifecycle.
        System.exit(0)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        super.dispatchGenericMotionEvent(event)
        gameInput.onMotionEvent(event)
        return true
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        super.dispatchKeyEvent(event)
        gameInput.onKeyEvent(event)
        return true
    }

    override fun onBackPressed() {
        retroDroid?.stop()
        retroDroid?.unloadGame()
    }

    private fun addFpsView() {
        val frameLayout = findViewById<FrameLayout>(R.id.game_layout)

        val fpsView = TextView(this)
        fpsView.textSize = 18f
        fpsView.setTextColor(Color.WHITE)
        fpsView.setShadowLayer(2f, 0f, 0f, Color.BLACK)

        frameLayout.addView(fpsView)

        fun updateFps() {
            // TODO FILIPPO... We should renable this when working on the layout.
            // fpsView.text = getString(R.string.fps_format, gameDisplay.fps, retroDroid?.fps ?: 0L)
            // fpsView.postDelayed({ updateFps() }, 1000)
        }
        updateFps()
    }

    private fun loadRetro(data: GameLoader.GameData) {
        try {
            val retroDroid = RetroDroid(gameDisplay, GameAudio(), gameInput, this, data.coreFile)
            lifecycle.addObserver(retroDroid)

            if (displayTouchInput) {
                initTouchInputs(data.game)
            }

            retroDroid.gameUnloaded
                .map { optionalSaveData ->
                    if (optionalSaveData is Some) {
                        val tmpFile = createTempFile()
                        tmpFile.writeBytes(optionalSaveData.value)
                        tmpFile.toOptional()
                    } else {
                        None
                    }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .autoDisposable(scope())
                .subscribeBy(
                    onNext = { optionalTmpFile ->
                        val resultData = Intent()
                        if (optionalTmpFile is Some) {
                            resultData.putExtra(EXTRA_GAME_ID, data.game.id)
                            resultData.putExtra(EXTRA_SAVE_FILE, optionalTmpFile.value.absolutePath)
                        }
                        setResult(Activity.RESULT_OK, resultData)
                        finish()
                    },
                    onError = { error ->
                        Timber.e(error, "Error unloading game")
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )

            retroDroid.loadGame(data.gameFile.absolutePath, data.saveData)
            retroDroid.start()

            this.game = data.game
            this.retroDroid = retroDroid
        } catch (ex: Exception) {
            Timber.e(ex, "Exception during retro initialization")
            finish()
        }
    }

    @dagger.Module
    class Module
}