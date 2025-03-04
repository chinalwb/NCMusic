package com.ssk.ncmusic.utils

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_MIN
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ssk.ncmusic.MainActivity
import com.ssk.ncmusic.R
import com.ssk.ncmusic.broadcast.MusicNotificationReceiver
import com.ssk.ncmusic.core.MusicPlayController
import com.ssk.ncmusic.core.NCApplication
import com.ssk.ncmusic.core.player.event.PauseSongEvent
import com.ssk.ncmusic.core.player.event.PlaySongEvent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Created by ssk on 2022/4/26.
 */
object MusicNotificationHelper {
    private const val CHANNEL_ID = "channel_id_music"
    private const val CHANNEL_NAME = "channel_name_music"
    const val NOTIFICATION_ID = 100

    private var mNotification: Notification? = null
    private var mRemoteViews: RemoteViews? = null
    private var mNotificationManager: NotificationManager? = null
    private var loadSongCoverJob: Job? = null

    fun getNotification() = mNotification

    init {
        EventBus.getDefault().register(this)
    }

    fun init(callback: () -> Unit) {
        mNotificationManager =
            NCApplication.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        initNotification()
        callback.invoke()
    }

    private fun initNotification() {
        loadSongCoverJob?.cancel()

        initRemoteViews()

        //再构建Notification
        val intent = Intent(NCApplication.context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            NCApplication.context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        //适配安卓8.0的消息渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                IMPORTANCE_MIN
            )
            channel.enableLights(false)
            channel.enableVibration(false)
            channel.setSound(null, null)
            mNotificationManager!!.createNotificationChannel(channel)
        }
        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(NCApplication.context, CHANNEL_ID)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)
                .setSmallIcon(R.drawable.ic_music_notification)
                .setSound(null)
                .setVibrate(null)
                .setSound(null)
                .setLights(0, 0, 0)
                .setCustomBigContentView(mRemoteViews)
                .setContent(mRemoteViews)

        mNotification = builder.build()

        updateNotificationUI()
    }

    private fun initRemoteViews() {
        mRemoteViews =
            RemoteViews(NCApplication.context.packageName, R.layout.layout_music_notification)

        //播放or暂停
        val playIntent = Intent(MusicNotificationReceiver.ACTION_MUSIC_NOTIFICATION).apply {
            putExtra(MusicNotificationReceiver.KEY_EXTRA, MusicNotificationReceiver.ACTION_PLAY)
        }
        val playPendingIntent = PendingIntent.getBroadcast(
            NCApplication.context,
            1,
            playIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        mRemoteViews?.setOnClickPendingIntent(R.id.ivPlay, playPendingIntent)

        //上一曲
        val preIntent = Intent(MusicNotificationReceiver.ACTION_MUSIC_NOTIFICATION).apply {
            putExtra(MusicNotificationReceiver.KEY_EXTRA, MusicNotificationReceiver.ACTION_PRE)
        }
        val prePendingIntent = PendingIntent.getBroadcast(
            NCApplication.context,
            2,
            preIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        mRemoteViews?.setOnClickPendingIntent(R.id.ivPre, prePendingIntent)

        //下一曲
        val nextIntent = Intent(MusicNotificationReceiver.ACTION_MUSIC_NOTIFICATION).apply {
            putExtra(MusicNotificationReceiver.KEY_EXTRA, MusicNotificationReceiver.ACTION_NEXT)
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            NCApplication.context,
            3,
            nextIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        mRemoteViews?.setOnClickPendingIntent(R.id.ivNext, nextPendingIntent)
    }

    @SuppressLint("CheckResult")
    private fun updateNotificationUI() {
        MusicPlayController.originSongList.getOrNull(MusicPlayController.curOriginIndex)?.let { bean ->
            mRemoteViews?.run {
                setImageViewResource(
                    R.id.ivCover,
                    R.drawable.ic_default_disk_cover
                )

                setTextViewText(R.id.tvSongName, bean.name)
                setTextViewText(R.id.tvAuthor, bean.ar[0].name)
                setImageViewResource(
                    R.id.ivPlay,
                    if (MusicPlayController.isPlaying()) R.drawable.ic_music_notification_pause else R.drawable.ic_music_notification_play
                )

                loadSongCoverJob?.cancel()
                loadSongCoverJob = GlobalScope.launch {
                    NCApplication.context.getImageBitmapByUrl(bean.al.picUrl)?.let {
                        val bitmapCover = BitmapUtil.getRoundedCornerBitmap(it, 30)
                        setImageViewBitmap(
                            R.id.ivCover,
                            BitmapUtil.getRoundedCornerBitmap(bitmapCover, 30)
                        )
                    }
                }
                mNotificationManager?.notify(NOTIFICATION_ID, mNotification)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: PauseSongEvent) {
        mRemoteViews?.run {
            setImageViewResource(R.id.ivPlay, R.drawable.ic_music_notification_play)
            mNotificationManager?.notify(NOTIFICATION_ID, mNotification)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: PlaySongEvent) {
        mRemoteViews?.run {
            setImageViewResource(R.id.ivPlay, R.drawable.ic_music_notification_pause)
            mNotificationManager?.notify(NOTIFICATION_ID, mNotification)
        }
    }

    private suspend fun Context.getImageBitmapByUrl(url: String): Bitmap? {
        val request = ImageRequest.Builder(this)
            .size(200)
            .data(url)
            .allowHardware(false)
            .build()
        val result = (imageLoader.execute(request) as SuccessResult).drawable
        return (result as BitmapDrawable).bitmap
    }
}

