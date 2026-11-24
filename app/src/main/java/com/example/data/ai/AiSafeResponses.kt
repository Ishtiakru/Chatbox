package com.example.data.ai

import java.util.concurrent.atomic.AtomicInteger

/**
 * Last-resort, non-error replies. When every provider is exhausted, cooling down,
 * or misconfigured, the router falls back to one of these so Saira never surfaces a
 * raw exception to the user — it always returns a calm, friendly message instead.
 */
object AiSafeResponses {

    private val rotation = AtomicInteger(0)

    private val LIMIT_REACHED = listOf(
        "Saira এর ফ্রি AI limit এই মুহূর্তে শেষ হয়ে গেছে। অল্পক্ষণ পরে আবার লিখুন — Auto-Reply সেটিংস-এ একটি Backup AI key যোগ করলে আর কোনো বাধা থাকবে না।",
        "আপাতত Saira এর free quota শেষ, তাই অন্য API-তে shift করা হচ্ছে। এক মিনিট পর আবার চেষ্টা করুন।",
        "Saira এখন ব্যস্ত (limit reached)। একটু পর দয়া করে আবার চেষ্টা করবেন।"
    )

    private val GENERIC = listOf(
        "দুঃখিত, Saira এই মুহূর্তে উত্তর দিতে পারছে না। একটু পরে আবার চেষ্টা করুন।",
        "Saira এখন অনলাইন নেই মনে হচ্ছে। অল্পক্ষণ পর আবার লিখুন।",
        "কানেকশনে একটু সমস্যা হয়েছে। দয়া করে একবার আবার চেষ্টা করুন।"
    )

    /** Picks a friendly message based on whether the failure was a quota/limit issue. */
    fun forError(error: Throwable?): String {
        val pool = if (AiErrorClassifier.isQuotaExhausted(error)) LIMIT_REACHED else GENERIC
        // Mask the sign bit so the index never goes negative after AtomicInteger overflow.
        return pool[(rotation.getAndIncrement() and Int.MAX_VALUE) % pool.size]
    }

    const val APP_CHECK_UNAVAILABLE =
        "Saira এর সিকিউরিটি টোকেন (App Check) মেয়াদ শেষ হয়ে গেছে। অ্যাপটি বন্ধ করে আবার খুলুন অথবা কিছুক্ষণ পর চেষ্টা করুন।"
}
