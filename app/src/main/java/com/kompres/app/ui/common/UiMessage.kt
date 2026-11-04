package com.kompres.app.ui.common

import androidx.annotation.StringRes

data class UiMessage(@StringRes val resId: Int, val args: List<Any> = emptyList())
