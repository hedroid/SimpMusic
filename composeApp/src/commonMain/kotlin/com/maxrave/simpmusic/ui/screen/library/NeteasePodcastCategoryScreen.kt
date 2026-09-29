package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.simpmusic.ui.component.CenterLoadingBox
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.NormalAppBar
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.NeteasePodcastCategoryViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.error
import simpmusic.composeapp.generated.resources.retry

/** 网易云播客分类电台列表页(分类 chips 进入,/djradio/hot offset 分页,近底追加) */
@Composable
fun NeteasePodcastCategoryScreen(
    navController: NavController,
    categoryId: Long,
    categoryName: String,
    innerPadding: PaddingValues,
    viewModel: NeteasePodcastCategoryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(categoryId) {
        viewModel.load(categoryId)
    }

    Column(Modifier.fillMaxSize()) {
        NormalAppBar(
            title = {
                Text(
                    text = categoryName,
                    style = typo().titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leftIcon = {
                androidx.compose.material3.IconButton(onClick = { navController.navigateUp() }) {
                    androidx.compose.material3.Icon(SimpIcons.ArrowBackIosNew, contentDescription = "Back")
                }
            },
        )
        if (uiState.loading && uiState.radios.isEmpty()) {
            CenterLoadingBox(Modifier.fillMaxSize())
            return@Column
        }
        val listState = rememberLazyListState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last >= listState.layoutInfo.totalItemsCount - 8
            }
        }
        LaunchedEffect(shouldLoadMore, uiState.hasMore) {
            if (shouldLoadMore && uiState.hasMore && !uiState.loadingMore && !uiState.loading) {
                viewModel.loadMore()
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                ),
        ) {
            if (uiState.radios.isEmpty() && !uiState.loading) {
                item(key = "category_error") {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(Res.string.error),
                            style = typo().bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (uiState.unavailable) {
                            Button(
                                onClick = { viewModel.retry() },
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Text(stringResource(Res.string.retry), style = typo().labelMedium)
                            }
                        }
                    }
                }
            }
            itemsIndexed(uiState.radios, key = { _, r -> "category_radio_" + r.id }) { index, radio ->
                Box(Modifier.padding(horizontal = 15.dp, vertical = 6.dp)) {
                NeteaseDjRadioCard(radio = radio, rank = index + 1) {
                    navController.navigate(
                        com.maxrave.simpmusic.ui.navigation.destination.list.NeteaseRadioDetailDestination(
                            radioId = radio.id,
                            radioName = radio.name,
                        ),
                    )
                }
                }
            }
            if (uiState.loadingMore) {
                item(key = "category_loading_more") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
            item(key = "category_end") {
                // 列表自身无 bottom contentPadding(电台卡自带 15dp 视觉边距),
                // 页尾走默认动态避让
                EndOfPage()
            }
        }
    }
}

