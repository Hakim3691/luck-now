package com.lucknow

import com.chaquo.python.android.PyApplication

/**
 * Base application class required by Chaquopy. The Python interpreter is
 * started lazily on the first call into [com.lucknow.analysis.PythonStatisticalAnalyzer],
 * so app launch stays fast on low-resource devices.
 */
class LuckNowApplication : PyApplication()
