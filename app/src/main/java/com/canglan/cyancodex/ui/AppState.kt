package com.canglan.cyancodex.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.canglan.cyancodex.data.Session
import com.canglan.cyancodex.data.User

/**
 * 全局可观察应用状态 (登录态/积分等)
 */
object AppState {
    var user by mutableStateOf<User?>(Session.user())
        private set
    var token by mutableStateOf<String?>(Session.token)
        private set
    var themeMode by mutableStateOf(Session.themeMode)
        private set

    fun applyThemeMode(v: String) {
        Session.themeMode = v
        themeMode = v
    }

    fun setLogin(u: User, t: String) {
        Session.token = t
        Session.saveUser(u)
        user = u
        token = t
    }

    fun logout() {
        Session.logout()
        user = null
        token = null
    }

    fun patchUser(
        points: Int? = null,
        aiUnlocked: Boolean? = null,
        nickname: String? = null,
        email: String? = null,
        gender: String? = null,
        avatar: String? = null,
    ) {
        val cur = user ?: return
        val upd = cur.copy(
            points = points ?: cur.points,
            aiUnlocked = aiUnlocked ?: cur.aiUnlocked,
            nickname = nickname ?: cur.nickname,
            email = email ?: cur.email,
            gender = gender ?: cur.gender,
            avatar = avatar ?: cur.avatar,
        )
        Session.saveUser(upd)
        user = upd
    }
}
