package com.example.agent

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class GitHubIdentity(val login: String, val repo: String, val canPush: Boolean)

data class GitHubRun(
    val id: Long,
    val name: String,
    val title: String,
    val status: String,
    val conclusion: String,
    val createdAt: String,
    val htmlUrl: String
)

data class GitHubRelease(val tag: String, val publishedAt: String, val assets: Map<String, String>)

/** Minimal GitHub REST client used by Saira's admin agent. */
class GitHubClient(private val token: String?, val repo: String = DEFAULT_REPO) {

    suspend fun verify(): Result<GitHubIdentity> = call {
        val user = JSONObject(request("GET", "https://api.github.com/user").second)
        val r = JSONObject(request("GET", "https://api.github.com/repos/$repo").second)
        val perms = r.optJSONObject("permissions")
        GitHubIdentity(
            login = user.optString("login"),
            repo = r.optString("full_name", repo),
            canPush = perms?.optBoolean("push") == true || perms?.optBoolean("admin") == true
        )
    }

    /** Starts a workflow_dispatch run on main. */
    suspend fun dispatch(workflowFile: String, inputs: Map<String, String>): Result<Unit> = call {
        val body = JSONObject().put("ref", "main").put("inputs", JSONObject(inputs as Map<*, *>))
        val (code, raw) = request(
            "POST",
            "https://api.github.com/repos/$repo/actions/workflows/$workflowFile/dispatches",
            body.toString()
        )
        if (code !in 200..299) throw IOException("dispatch HTTP $code: ${raw.take(200)}")
    }

    suspend fun runs(workflowFile: String? = null, limit: Int = 10): Result<List<GitHubRun>> = call {
        val url = if (workflowFile != null) {
            "https://api.github.com/repos/$repo/actions/workflows/$workflowFile/runs?per_page=$limit"
        } else {
            "https://api.github.com/repos/$repo/actions/runs?per_page=$limit"
        }
        val arr = JSONObject(request("GET", url).second).optJSONArray("workflow_runs") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GitHubRun(
                id = o.optLong("id"),
                name = o.optString("name"),
                title = o.optString("display_title"),
                status = o.optString("status"),
                conclusion = o.optString("conclusion").takeIf { it != "null" }.orEmpty(),
                createdAt = o.optString("created_at"),
                htmlUrl = o.optString("html_url")
            )
        }
    }

    suspend fun run(runId: Long): Result<GitHubRun> = call {
        val o = JSONObject(request("GET", "https://api.github.com/repos/$repo/actions/runs/$runId").second)
        GitHubRun(
            id = o.optLong("id"),
            name = o.optString("name"),
            title = o.optString("display_title"),
            status = o.optString("status"),
            conclusion = o.optString("conclusion").takeIf { it != "null" }.orEmpty(),
            createdAt = o.optString("created_at"),
            htmlUrl = o.optString("html_url")
        )
    }

    /** Notice/error annotation messages of every job in a run (agent summaries live here). */
    suspend fun runMessages(runId: Long): Result<List<String>> = call {
        val jobs = JSONObject(
            request("GET", "https://api.github.com/repos/$repo/actions/runs/$runId/jobs").second
        ).optJSONArray("jobs") ?: JSONArray()
        val out = mutableListOf<String>()
        for (i in 0 until jobs.length()) {
            val jobId = jobs.getJSONObject(i).optLong("id")
            val ann = JSONArray(
                request("GET", "https://api.github.com/repos/$repo/check-runs/$jobId/annotations").second
            )
            for (j in 0 until ann.length()) {
                val msg = ann.getJSONObject(j).optString("message")
                if (msg.isNotBlank() && !msg.contains("Node.js")) out += msg
            }
        }
        out
    }

    suspend fun latestRelease(): Result<GitHubRelease> = call {
        val o = JSONObject(request("GET", "https://api.github.com/repos/$repo/releases/latest").second)
        val assets = o.optJSONArray("assets") ?: JSONArray()
        GitHubRelease(
            tag = o.optString("tag_name"),
            publishedAt = o.optString("published_at"),
            assets = (0 until assets.length()).associate { i ->
                val a = assets.getJSONObject(i)
                a.optString("name") to a.optString("browser_download_url")
            }
        )
    }

    suspend fun recentCommits(limit: Int = 5): Result<List<String>> = call {
        val arr = JSONArray(
            request("GET", "https://api.github.com/repos/$repo/commits?sha=main&per_page=$limit").second
        )
        (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            c.optString("sha").take(7) + " " +
                c.optJSONObject("commit")?.optString("message").orEmpty().lineSequence().first().take(90)
        }
    }

    private suspend fun <T> call(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun request(method: String, url: String, body: String? = null): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.setRequestProperty("User-Agent", "Chatbox-Saira-Agent")
            if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.readBytes()?.toString(Charsets.UTF_8).orEmpty()
            if (code == 401) throw IOException("GitHub token ভুল বা মেয়াদ শেষ (401)।")
            if (code == 403 && raw.contains("rate limit", true)) throw IOException("GitHub rate limit — একটু পরে চেষ্টা করুন।")
            if (code == 404 && method == "GET") throw IOException("GitHub: পাওয়া যায়নি (404) — token-এ এই repo-র access আছে তো?")
            if (method == "GET" && code !in 200..299) throw IOException("GitHub HTTP $code: ${raw.take(160)}")
            return code to raw
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEFAULT_REPO = "Ishtiakru/Chatbox"
        const val AGENT_WORKFLOW = "saira-agent.yml"
        const val RULES_WORKFLOW = "firestore-rules.yml"

        fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}
