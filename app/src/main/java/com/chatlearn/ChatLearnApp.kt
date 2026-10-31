package com.chatlearn

import android.app.Application

class ChatLearnApp : Application() {
    // 처음 쓸 때 만든다: Robolectric 테스트에는 AndroidKeyStore가 없어서 시작 시점에 만들면 안 된다.
    val container: AppContainer by lazy { AppContainer(this) }
}
