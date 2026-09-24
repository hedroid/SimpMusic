package com.maxrave.simpmusic.di

import com.maxrave.simpmusic.viewModel.AlbumViewModel
import com.maxrave.simpmusic.utils.VersionManager
import com.maxrave.simpmusic.viewModel.AnalyticsViewModel
import com.maxrave.simpmusic.viewModel.ListenTogetherSettingsViewModel
import com.maxrave.simpmusic.viewModel.ListenTogetherViewModel
import com.maxrave.simpmusic.viewModel.ArtistViewModel
import com.maxrave.simpmusic.viewModel.HomeViewModel
import com.maxrave.simpmusic.viewModel.ImportViewModel
import com.maxrave.simpmusic.viewModel.LibraryDynamicPlaylistViewModel
import com.maxrave.simpmusic.viewModel.LibraryMutationBus
import com.maxrave.simpmusic.viewModel.LibraryViewModel
import com.maxrave.simpmusic.viewModel.LocalPlaylistViewModel
import com.maxrave.simpmusic.viewModel.LogInViewModel
import com.maxrave.simpmusic.viewModel.NeteaseHomeViewModel
import com.maxrave.simpmusic.viewModel.NeteaseLoginViewModel
import com.maxrave.simpmusic.viewModel.NeteaseMixViewModel
import com.maxrave.simpmusic.viewModel.NeteaseTagViewModel
import com.maxrave.simpmusic.viewModel.MoodViewModel
import com.maxrave.simpmusic.viewModel.MoreAlbumsViewModel
import com.maxrave.simpmusic.viewModel.MoreSongsViewModel
import com.maxrave.simpmusic.viewModel.NotificationViewModel
import com.maxrave.simpmusic.viewModel.NowPlayingBottomSheetViewModel
import com.maxrave.simpmusic.viewModel.PlaylistViewModel
import com.maxrave.simpmusic.viewModel.PodcastViewModel
import com.maxrave.simpmusic.viewModel.RecentlySongsViewModel
import com.maxrave.simpmusic.viewModel.SearchViewModel
import com.maxrave.simpmusic.viewModel.AutoEqViewModel
import com.maxrave.simpmusic.viewModel.SettingsViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.SongSelectionViewModel
import com.maxrave.simpmusic.viewModel.WrappedViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule =
    module {
        // 库页本地回写事件总线:子页写操作成功 → 库页原地更新(不做返回网络刷新)
        single { LibraryMutationBus() }
        single {
            SharedViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        single {
            SearchViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            SongSelectionViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            NowPlayingBottomSheetViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LibraryViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LibraryDynamicPlaylistViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            ImportViewModel(
                get(),
            )
        }
        viewModel {
            AlbumViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            HomeViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            AutoEqViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            SettingsViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            ArtistViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            PlaylistViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LogInViewModel(
                get(),
            )
        }
        viewModel {
            NeteaseLoginViewModel(
                get(),
                get(),
            )
        }
        // single 而非 viewModel:{}——koinViewModel() 会把实例 scope 到导航栈条目,
        // 底栏 tab 每次切走再回来都重建 VM,整页行/分区全部回 Loading 占位重拉
        // (就是"切个 tab 回来满屏转圈"的根因)。single 让 tab 往返零重拉,兑现
        // "tab 切换往返靠 VM 状态"的设计;进程重启自然重来。
        single {
            NeteaseHomeViewModel(
                get(),
            )
        }
        viewModel {
            NeteaseTagViewModel(
                get(),
            )
        }
        single {
            NeteaseMixViewModel(
                get(),
            )
        }
        viewModel {
            PodcastViewModel(
                get(),
            )
        }
        viewModel {
            MoreAlbumsViewModel(
                get(),
            )
        }
        viewModel {
            MoreSongsViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            RecentlySongsViewModel(
                get(),
            )
        }
        viewModel {
            LocalPlaylistViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            NotificationViewModel(
                get(),
            )
        }
        viewModel {
            MoodViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            AnalyticsViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            WrappedViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            ListenTogetherSettingsViewModel(get())
        }
        viewModel {
            ListenTogetherViewModel(
                repository = get(),
                dataStore = get(),
                bridge = get(),
            )
        }

    }
