package com.secondproject.datasortgallery.core.common

sealed class Resource<out T> {
    object Loading : Resource<Nothing>()
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val exception: Throwable, val message: String? = exception.localizedMessage) : Resource<Nothing>()
}