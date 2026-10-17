package be.wimvandonck.reader.ui.instapaper

import androidx.annotation.StringRes
import be.wimvandonck.reader.R
import be.wimvandonck.reader.instapaper.InstapaperProblem

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
