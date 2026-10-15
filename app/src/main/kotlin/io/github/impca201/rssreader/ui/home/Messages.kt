package io.github.impca201.rssreader.ui.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.AddFeedResult
import io.github.impca201.rssreader.data.FeedProblem

/** Why a feed could not be refreshed, in words. */
@Composable
fun problemText(problem: FeedProblem): String = stringResource(problem.textRes())

@StringRes
fun FeedProblem.textRes(): Int =
    when (this) {
        FeedProblem.Offline -> R.string.problem_offline
        FeedProblem.NotFound -> R.string.problem_not_found
        FeedProblem.ServerError -> R.string.problem_server
        FeedProblem.TooLarge -> R.string.problem_too_large
        FeedProblem.NotAFeed -> R.string.problem_not_a_feed
    }

/** The error to show in the Add feed dialog, or null when the feed was added. */
@StringRes
fun AddFeedResult.errorRes(): Int? =
    when (this) {
        is AddFeedResult.Added -> null
        AddFeedResult.AlreadyAdded -> R.string.add_feed_already
        AddFeedResult.InvalidAddress -> R.string.add_feed_invalid
        AddFeedResult.NoFeed -> R.string.add_feed_no_feed
        is AddFeedResult.Failed -> problem.textRes()
    }
