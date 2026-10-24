package com.jolt.ui.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

private val ItemHeight = 64.dp
private const val VISIBLE = 3

/**
 * A scrolling wheel of [count] options that snaps to the centre row.
 * [initial] is only read once; the wheel owns its position after that.
 */
@Composable
fun WheelPicker(
    count: Int,
    initial: Int,
    onSelect: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
    cyclic: Boolean = true,
    width: Dp = 96.dp,
) {
    // Cyclic wheels fake infinity with a huge list that starts in the middle.
    val total = if (cyclic) count * 1000 else count
    val startIndex = if (cyclic) count * 500 + initial else initial
    val state = rememberLazyListState(startIndex)
    val fling = rememberSnapFlingBehavior(state)
    val itemPx = with(LocalDensity.current) { ItemHeight.toPx() }
    val select by rememberUpdatedState(onSelect)

    val centred by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { centred }.distinctUntilChanged().collect { select(it % count) }
    }

    Box(modifier.width(width).height(ItemHeight * VISIBLE), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = ItemHeight * (VISIBLE / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(total) { index ->
                val distance = abs(index - centred)
                Box(
                    Modifier
                        .height(ItemHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = if (distance == 0) 1f else 0.28f
                            val scale = if (distance == 0) 1f else 0.82f
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(index % count),
                        style = MaterialTheme.typography.displaySmall,
                        color = if (distance == 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
