package be.wimvandonck.content.ui.instapaper

import androidx.annotation.StringRes
import be.wimvandonck.content.R
import be.wimvandonck.content.instapaper.InstapaperProblem

/** What to tell the user about a failed Instapaper call. */
@StringRes
fun InstapaperProblem.messageRes(): Int =
    when (this) {
        InstapaperProblem.SignedOut -> R.string.instapaper_problem_signed_out
        InstapaperProblem.NotApproved -> R.string.instapaper_problem_not_approved
        InstapaperProblem.RateLimited -> R.string.instapaper_problem_rate_limited
        InstapaperProblem.Offline -> R.string.problem_offline
        InstapaperProblem.ServerError -> R.string.problem_server
        InstapaperProblem.Invalid -> R.string.instapaper_problem_invalid
    }
