package io.github.emmanuelquintana.capri

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Every few hours, with the network on, even when Capri is off: a look at GitHub for a newer
 * release, and a system notification when there is one (Updater.checkInBackground, once per version).
 */
class UpdateJob : JobService() {
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(Lang.wrap(newBase))

    override fun onStartJob(params: JobParameters): Boolean {
        Updater.checkInBackground(this, force = true, done = { jobFinished(params, false) }) { r ->
            CapriService.current?.newRelease(r)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val ID = 4701

        /** Once is enough (it survives restarts); calling it again keeps the same job. */
        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(ID) != null) return
            val job = JobInfo.Builder(ID, ComponentName(context, UpdateJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(Updater.CHECK_EVERY)
                .setPersisted(true)
                .build()
            runCatching { js.schedule(job) }.onFailure { Diag.add("W", "update job: ${it.message}") }
        }
    }
}
