package com.jvmd.notesbridge.io

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit

object CleanupScheduler {
    private const val JOB_ID = 4_201

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val job =
            JobInfo
                .Builder(JOB_ID, ComponentName(context, CleanupJobService::class.java))
                .setMinimumLatency(FileManager.OUTPUT_RETENTION_MILLIS + TimeUnit.MINUTES.toMillis(5))
                .setOverrideDeadline(FileManager.OUTPUT_RETENTION_MILLIS + TimeUnit.HOURS.toMillis(6))
                .setPersisted(false)
                .build()
        scheduler.schedule(job)
    }
}

class CleanupJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        FileManager(applicationContext).cleanUp()
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
