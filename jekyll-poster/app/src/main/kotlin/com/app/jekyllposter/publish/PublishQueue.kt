package com.app.jekyllposter.publish

import android.content.Context

/** Where queued posts are handed over to be sent: WorkManager in the app, the publisher at once in tests. */
interface PublishQueue {
    /** Publishes post [id], not before [sendAfter] (epoch millis) when it has one. */
    fun enqueue(id: Long, sendAfter: Long?)

    /** Starts [id]'s publish again now, past WorkManager's backoff, still not before [sendAfter]. */
    fun retryNow(id: Long, sendAfter: Long?)

    /** The writer's Send now. */
    fun sendNow(id: Long)
}

class WorkManagerQueue(private val context: Context) : PublishQueue {
    override fun enqueue(id: Long, sendAfter: Long?) = PublishWorker.enqueue(context, id, sendAfter)
    override fun retryNow(id: Long, sendAfter: Long?) = PublishWorker.retryNow(context, id, sendAfter)
    override fun sendNow(id: Long) = PublishWorker.sendNow(context, id)
}
