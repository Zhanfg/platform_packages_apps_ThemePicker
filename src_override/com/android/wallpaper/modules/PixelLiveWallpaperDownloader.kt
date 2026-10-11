/*
 * SPDX-FileCopyrightText: The uwuAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.wallpaper.modules

import android.app.Activity
import android.app.WallpaperInfo
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.service.wallpaper.WallpaperService
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.android.wallpaper.picker.data.CommonWallpaperData
import com.android.wallpaper.picker.data.DownloadableWallpaperData
import com.android.wallpaper.picker.data.LiveWallpaperData
import com.android.wallpaper.picker.data.WallpaperModel
import com.android.wallpaper.picker.preview.data.util.LiveWallpaperDownloader
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Drives Pixel's downloadable-wallpaper service for ThemePicker previews. */
@ActivityRetainedScoped
class PixelLiveWallpaperDownloader @Inject constructor() : LiveWallpaperDownloader {
    private val isReady = MutableStateFlow(false)
    override val isDownloaderReady: Flow<Boolean> = isReady.asStateFlow()

    private var activity: Activity? = null
    private var appContext: Context? = null
    private var wallpaperData: DownloadableWallpaperData? = null
    private var commonWallpaperData: CommonWallpaperData? = null
    private var messenger: Messenger? = null
    private var listener: LiveWallpaperDownloader.LiveWallpaperDownloadListener? = null
    private var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var isBound = false

    private val replyMessenger =
        Messenger(
            Handler(Looper.getMainLooper()) { message ->
                handleMessage(message)
            }
        )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            messenger = Messenger(binder)
            isReady.value = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            messenger = null
            isReady.value = false
        }
    }

    override fun initiateDownloadableService(
        activity: Activity,
        wallpaperData: WallpaperModel.StaticWallpaperModel,
        intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        cleanup()
        val downloadableData = wallpaperData.downloadableWallpaperData ?: return
        this.activity = activity
        this.appContext = activity.applicationContext
        this.wallpaperData = downloadableData
        this.commonWallpaperData = wallpaperData.commonWallpaperData
        this.intentSenderLauncher = intentSenderLauncher

        val intent = downloadableIntent(downloadableData.systemWallpaperInfo)
        try {
            isBound = activity.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!isBound) {
                Log.w(TAG, "Pixel wallpaper download service is unavailable")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied binding Pixel wallpaper download service", e)
        }
    }

    override fun cleanup() {
        val oldActivity = activity
        if (isBound && oldActivity != null) {
            try {
                oldActivity.unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Pixel wallpaper download service was already unbound", e)
            }
        }
        isBound = false
        messenger = null
        listener = null
        activity = null
        appContext = null
        wallpaperData = null
        commonWallpaperData = null
        intentSenderLauncher = null
        isReady.value = false
    }

    override fun downloadWallpaper(
        listener: LiveWallpaperDownloader.LiveWallpaperDownloadListener
    ) {
        val service = messenger
        if (service == null) {
            listener.onDownloadFailed()
            return
        }
        this.listener = listener
        sendMessage(service, MESSAGE_START_DOWNLOAD)
    }

    override fun cancelDownloadWallpaper(): Boolean {
        messenger?.let { sendMessage(it, MESSAGE_CANCEL_DOWNLOAD) }
        return false
    }

    private fun handleMessage(message: Message): Boolean {
        when (message.what) {
            MESSAGE_DOWNLOAD_SUCCEEDED -> finishDownloadSuccessfully()
            MESSAGE_DOWNLOAD_FAILED, MESSAGE_DOWNLOAD_CANCELLED -> {
                listener?.onDownloadFailed()
                listener = null
            }
            MESSAGE_CONFIRM_CANCEL -> showCancelConfirmation()
            MESSAGE_FINISH_PICKER -> activity?.finish()
            MESSAGE_LAUNCH_INTENT_SENDER -> launchIntentSender(message)
            else -> return false
        }
        return true
    }

    private fun finishDownloadSuccessfully() {
        val context = appContext ?: return failDownload()
        val downloadableData = wallpaperData ?: return failDownload()
        val commonData = commonWallpaperData ?: return failDownload()
        val component = downloadableData.systemWallpaperInfo.component
        val intent = Intent(WallpaperService.SERVICE_INTERFACE).setComponent(component)
        val resolveInfo = context.packageManager
            .queryIntentServices(intent, PackageManager.GET_META_DATA)
            .firstOrNull()
        if (resolveInfo == null) {
            Log.e(TAG, "Downloaded Pixel wallpaper service is still unavailable: $component")
            return failDownload()
        }

        try {
            val systemWallpaperInfo = WallpaperInfo(context, resolveInfo)
            listener?.onDownloadSuccess(
                WallpaperModel.LiveWallpaperModel(
                    commonWallpaperData = commonData,
                    liveWallpaperData = LiveWallpaperData(
                        groupName = downloadableData.groupName,
                        systemWallpaperInfo = systemWallpaperInfo,
                        isTitleVisible = downloadableData.isTitleVisible,
                        isApplied = downloadableData.isApplied,
                        isEffectWallpaper = false,
                        effectNames = null,
                    ),
                    creativeWallpaperData = null,
                    internalLiveWallpaperData = null,
                )
            )
            listener = null
        } catch (e: Exception) {
            Log.e(TAG, "Unable to reload downloaded Pixel wallpaper", e)
            failDownload()
        }
    }

    private fun failDownload() {
        listener?.onDownloadFailed()
        listener = null
    }

    private fun showCancelConfirmation() {
        val currentActivity = activity ?: return
        android.app.AlertDialog.Builder(currentActivity)
            .setTitle("Cancel wallpaper download?")
            .setMessage("The wallpaper is still downloading.")
            .setPositiveButton(
                "Cancel download",
                DialogInterface.OnClickListener { _, _ ->
                    messenger?.let { sendMessage(it, MESSAGE_CONFIRM_CANCELLED) }
                },
            )
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchIntentSender(message: Message) {
        val intent = message.obj as? Intent ?: return
        val sender = intent.getParcelableExtra<android.content.IntentSender>(EXTRA_INTENT_SENDER)
            ?: return
        val fillIn = intent.getParcelableExtra<Intent>(EXTRA_FILL_IN_INTENT)
        val request = IntentSenderRequest.Builder(sender)
            .setFillInIntent(fillIn)
            .setFlags(
                intent.getIntExtra(EXTRA_FLAGS_MASK, 0),
                intent.getIntExtra(EXTRA_FLAGS_VALUE, 0),
            )
            .build()
        intentSenderLauncher?.launch(request)
    }

    private fun sendMessage(service: Messenger, what: Int) {
        try {
            service.send(Message.obtain(null, what).apply { replyTo = replyMessenger })
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed sending message $what to Pixel wallpaper service", e)
            failDownload()
        }
    }

    private fun downloadableIntent(info: WallpaperInfo): Intent =
        Intent(ACTION_DOWNLOAD_LIVE_WALLPAPER)
            .setComponent(ComponentName(PACKAGE_NAME, DOWNLOAD_SERVICE))
            .putExtra(EXTRA_WALLPAPER_INFO, info)

    companion object {
        private const val TAG = "PixelLiveWallpaperDownloader"
        private const val PACKAGE_NAME = "com.google.pixel.livewallpaper"
        private const val DOWNLOAD_SERVICE = "com.google.pixel.livewallpaper.split.DownloadService"
        private const val ACTION_DOWNLOAD_LIVE_WALLPAPER =
            "com.google.pixel.livewallpaper.action.DOWNLOAD_LIVE_WALLPAPER"
        private const val EXTRA_WALLPAPER_INFO = "android.live_wallpaper.info"
        private const val EXTRA_INTENT_SENDER = "com.google.pixel.livewallpaper.sender"
        private const val EXTRA_FILL_IN_INTENT = "com.google.pixel.livewallpaper.fill_in"
        private const val EXTRA_FLAGS_MASK = "com.google.pixel.livewallpaper.flags_mask"
        private const val EXTRA_FLAGS_VALUE = "com.google.pixel.livewallpaper.flags_value"
        private const val MESSAGE_DOWNLOAD_SUCCEEDED = 1
        private const val MESSAGE_START_DOWNLOAD = 1
        private const val MESSAGE_CANCEL_DOWNLOAD = 2
        private const val MESSAGE_DOWNLOAD_FAILED = 3
        private const val MESSAGE_DOWNLOAD_CANCELLED = 4
        private const val MESSAGE_CONFIRM_CANCEL = 5
        private const val MESSAGE_FINISH_PICKER = 6
        private const val MESSAGE_LAUNCH_INTENT_SENDER = 7
        private const val MESSAGE_CONFIRM_CANCELLED = 3
    }
}
