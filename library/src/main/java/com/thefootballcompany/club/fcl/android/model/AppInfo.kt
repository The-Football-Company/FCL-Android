package com.thefootballcompany.club.fcl.android.model

import android.util.ArrayMap

/**
 * Created by muriel on 21.04.2022..
 */
data class AppInfo(
    val title: String,
    val location: String,
    val icon: String,
    val variables: Map<String, String>
)

class AppBuilderBuilder {

    private var location: String? = null
    private var title: String? = null
    private var icon: String? = null
    private val variables: ArrayMap<String, String> = ArrayMap()

    fun title(title: String) = apply { this.title = title }

    fun location(location: String) = apply {
        this.location = location
    }

    fun icon(icon: String) = apply { this.icon = icon }

    fun addVar(key: String, value: String) = apply {
        this.variables[key] = value
    }

    fun build(): AppInfo {
        return AppInfo(
            this.title ?: "FCL-Android",
            this.location ?: "http://foo.com",
            icon ?: "http://foo.com/icon",
            variables = variables.toMap()
        )
    }
}
