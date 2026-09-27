package com.hebo.codex.domain

import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal suspend fun MutableStateFlow<ConversationState>.loadTurnDetails(
    turnId: String,
    loadItems: suspend () -> List<ConversationItem>,
) {
    val loadingState = TurnDetailsState.Loading()
    val initialTurn: ConversationTurn
    while (true) {
        val current = value
        val turn = current.history.turns.singleOrNull { it.id == turnId } ?: return
        if (turn.detailsState == TurnDetailsState.Loaded || turn.detailsState is TurnDetailsState.Loading) return
        val loading = current.updateTurn(turnId) { it.copy(detailsState = loadingState) }
        if (compareAndSet(current, loading)) {
            initialTurn = turn
            break
        }
    }
    try {
        val items = loadItems()
        update { current ->
            current.updateTurn(turnId) { turn ->
                if (turn.detailsState !== loadingState) return@updateTurn turn
                // 请求期间收到的实时条目更新优先于此次读取的快照
                val initialItems = initialTurn.items.associateBy(ConversationItem::id)
                val changedItems = turn.items.filter { it != initialItems[it.id] }
                turn.copy(
                    items = changedItems.fold(items) { merged, item -> merged.upsertConversationItem(item) },
                    detailsState = TurnDetailsState.Loaded,
                )
            }
        }
    } catch (error: CancellationException) {
        updateDetailsState(turnId, loadingState, TurnDetailsState.Unloaded)
        throw error
    } catch (error: Throwable) {
        updateDetailsState(turnId, loadingState, TurnDetailsState.Failed(error.toUserFacingError("无法加载处理过程")))
    }
}

private fun MutableStateFlow<ConversationState>.updateDetailsState(
    turnId: String,
    loadingState: TurnDetailsState.Loading,
    detailsState: TurnDetailsState,
) {
    update { current ->
        current.updateTurn(turnId) { turn ->
            if (turn.detailsState === loadingState) turn.copy(detailsState = detailsState) else turn
        }
    }
}

private fun ConversationState.updateTurn(
    turnId: String,
    transform: (ConversationTurn) -> ConversationTurn,
): ConversationState = copy(
    history = history.copy(turns = history.turns.map { if (it.id == turnId) transform(it) else it }),
)
