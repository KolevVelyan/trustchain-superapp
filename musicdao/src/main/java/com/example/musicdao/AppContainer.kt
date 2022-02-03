package com.example.musicdao

import TorrentCache
import TorrentEngine
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import com.example.musicdao.domain.usecases.CreateReleaseUseCase
import com.example.musicdao.domain.usecases.GetReleaseUseCase
import com.example.musicdao.domain.usecases.torrents.DownloadIntentUseCase
import com.example.musicdao.domain.usecases.torrents.GetTorrentStatusFlowUseCase
import com.example.musicdao.ipv8.MusicCommunity
import com.example.musicdao.repositories.ReleaseRepository
import com.example.musicdao.repositories.SwarmHealthRepository
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SessionParams
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.swig.settings_pack
import java.nio.file.Paths

object AppContainer {
    lateinit var getTorrentStatusFlowUseCase: GetTorrentStatusFlowUseCase
    lateinit var downloadIntentuseCase: DownloadIntentUseCase
    lateinit var getReleaseUseCase: GetReleaseUseCase
    lateinit var createReleaseUseCase: CreateReleaseUseCase
    lateinit var sessionManager: SessionManager

    lateinit var torrentEngine: TorrentEngine
    lateinit var torrentCache: TorrentCache

    lateinit var currentCallback: (List<Uri>) -> Unit

    //    lateinit var swarmHealthMap = mutableMapOf<Sha1Hash, SwarmHealth>()
    lateinit var swarmHealthRepository: SwarmHealthRepository
    lateinit var releaseRepository: ReleaseRepository
    lateinit var activity: MusicActivity

    @RequiresApi(Build.VERSION_CODES.O)
    fun provide(
        applicationContext: Context,
        musicCommunity: MusicCommunity,
        _activity: MusicActivity
    ) {
        activity = _activity
        sessionManager = SessionManager().apply {
            start(createSessionParams(applicationContext))
        }
        releaseRepository = ReleaseRepository(musicCommunity)
        torrentEngine = TorrentEngine(sessionManager)
        torrentCache = TorrentCache(torrentEngine, Paths.get("${applicationContext.cacheDir}"))

        createReleaseUseCase = CreateReleaseUseCase(
            releaseRepository,
            torrentCache
        )
        getReleaseUseCase =
            GetReleaseUseCase(releaseRepository, torrentCache)

        downloadIntentuseCase = DownloadIntentUseCase(torrentCache)
        getTorrentStatusFlowUseCase = GetTorrentStatusFlowUseCase(torrentCache)

    }

    private fun createSessionParams(applicationContext: Context): SessionParams {
        val settingsPack = SettingsPack()

        val port =
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .getString("musicdao_port", "10021")
                ?.toIntOrNull()
        if (port != null) {
            val interfaceFormat = "0.0.0.0:%1\$d,[::]:%1\$d"
            settingsPack.listenInterfaces(String.format(interfaceFormat, port))
        }

        settingsPack.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true);
        settingsPack.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true);
        settingsPack.setBoolean(settings_pack.bool_types.listen_system_port_fallback.swigValue(), false);
        settingsPack.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false);
        settingsPack.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false);

        return SessionParams(settingsPack)
    }
}
