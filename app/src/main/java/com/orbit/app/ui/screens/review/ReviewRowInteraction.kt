package com.orbit.app.ui.screens.review

internal fun reviewRowClick(
    item: ReviewItem,
    onReviewItemSelected: (ReviewItem) -> Unit,
): () -> Unit = { onReviewItemSelected(item) }
