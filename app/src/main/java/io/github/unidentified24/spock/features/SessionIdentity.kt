package io.github.unidentified24.spock.features

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

object SessionIdentity {
    val active=MutableStateFlow("default")
    val id get()=active.value
    fun initialize(context:Context){active.value=context.getSharedPreferences("spock_profiles",0).getString("active","default") ?: "default"}
    fun select(context:Context,id:String){require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}")));context.getSharedPreferences("spock_profiles",0).edit().putString("active",id).commit();active.value=id}
}
