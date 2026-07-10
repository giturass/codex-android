package com.hebo.codex

import android.app.Application

class CodexApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.NONE) { AppContainer(this) }
}

