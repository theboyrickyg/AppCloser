package com.rickyboy.appsweep

import android.content.ActivityNotFoundException
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST = 4242
        private const val NOTIFY_REQUEST = 4243
    }

    private lateinit var sweeper: Sweeper
    private lateinit var ramGauge: RamGaugeView
    private lateinit var cleanGauge: CleanGaugeView
    private lateinit var ramUsedText: TextView
    private lateinit var ramFreeText: TextView
    private lateinit var appsCountText: TextView
    private lateinit var statusCard: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var statusText2: TextView
    private lateinit var choiceBox: View
    private lateinit var stepImage: View
    private lateinit var statusButton: Button
    private lateinit var statusButton2: Button
    private lateinit var codeRow: LinearLayout
    private lateinit var codeInput: EditText
    private lateinit var pairButton: Button
    private lateinit var shizukuButton: Button
    private lateinit var linkButton: Button
    private lateinit var sweepButton: Button
    private lateinit var resultText: TextView
    private lateinit var resultRow: LinearLayout
    private lateinit var allCleanView: View
    private var closeX: Button? = null     // not on the TV layout
    private lateinit var tvAutoButton: Button
    private var sweptThisSession = false
    private var awaitingStep = -1   // which Easy step to show after coming back from Settings
    private lateinit var statClosed: TextView
    private lateinit var statRam: TextView
    private lateinit var statCache: TextView
    private lateinit var statsText: TextView
    private lateinit var listHeader: TextView
    private lateinit var listHint: TextView
    private lateinit var adapter: AppAdapter

    private var busy = false
    private var ready = false
    private var currentApps: List<RunningApp> = emptyList()
    private var scanJob: Job? = null
    private var usageGranted = false
    private var lastTotalCache = -1L
    private var cacheJob: kotlinx.coroutines.Job? = null
    private val prefs by lazy { getSharedPreferences("appsweep", Context.MODE_PRIVATE) }

    private var setupJob: Job? = null

    /** TV with neither Pro nor Easy set up: App Closer opens each app's page for you to press Force stop. */
    private val tvGuided: Boolean get() = isTv && !Shell.isReady() && !SweepAccessibilityService.isOn()

    // Guided closing state (TV)
    private var guidedActive = false
    private val guidedQueue = ArrayDeque<RunningApp>()
    private var guidedCurrent: RunningApp? = null
    private var guidedTotal = 0
    private var guidedClosed = 0
    private var guidedRamBefore = 0L
    private var guidedDiskBefore = 0L
    private var guidedNextJob: Job? = null
    private var autoSweepPending = false
    private var unlockDialog: AlertDialog? = null
    private lateinit var instantLink: Button
    private var titleTaps = 0
    private var titleTapStart = 0L

    /** TV without Pro: the list is always the TV's launched apps, whether closing is guided or automatic. */
    private val tvList: Boolean get() = isTv && !Shell.isReady()

    /** Easy mode = no debugging connection, but the accessibility switch is on. */
    private val easyMode: Boolean get() = !Shell.isReady() && SweepAccessibilityService.isOn()
    private var setupChoice: String?
        get() = prefs.getString("setup_choice", null)
        set(v) { prefs.edit().putString("setup_choice", v).apply() }
    private val isTv by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }

    private val onShellReady: () -> Unit = { runOnUiThread { checkConnection(userAsked = false) } }
    private val binderReceived = Shizuku.OnBinderReceivedListener { checkConnection(userAsked = false) }
    private val binderDead = Shizuku.OnBinderDeadListener {
        Shell.onShizukuDead()
        checkConnection(userAsked = false)
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> checkConnection(userAsked = false) }

    // ---------- Lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sweeper = Sweeper(applicationContext)

        ramGauge = findViewById(R.id.ramGauge)
        cleanGauge = findViewById(R.id.cleanGauge)
        ramUsedText = findViewById(R.id.ramUsedText)
        ramFreeText = findViewById(R.id.ramFreeText)
        appsCountText = findViewById(R.id.appsCountText)
        statusCard = findViewById(R.id.statusCard)
        statusText = findViewById(R.id.statusText)
        statusText2 = findViewById(R.id.statusText2)
        choiceBox = findViewById(R.id.choiceBox)
        stepImage = findViewById(R.id.stepImage)
        // words in the setup steps that are buttons themselves
        statusText.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        statusText.highlightColor = android.graphics.Color.TRANSPARENT
        statusText2.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        statusText2.highlightColor = android.graphics.Color.TRANSPARENT
        statusButton = findViewById(R.id.statusButton)
        statusButton2 = findViewById(R.id.statusButton2)
        codeRow = findViewById(R.id.codeRow)
        codeInput = findViewById(R.id.codeInput)
        pairButton = findViewById(R.id.pairButton)
        shizukuButton = findViewById(R.id.shizukuButton)
        linkButton = findViewById(R.id.linkButton)
        pairButton.setOnClickListener { pairFromApp() }
        shizukuButton.setOnClickListener { useShizuku() }
        sweepButton = findViewById(R.id.sweepButton)
        resultText = findViewById(R.id.resultText)
        resultRow = findViewById(R.id.resultRow)
        allCleanView = findViewById(R.id.allCleanView)
        closeX = findViewById(R.id.closeX)
        closeX?.setOnClickListener {
            // straight to the home screen, so nothing App Closer opened is left showing
            try {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
            finishAndRemoveTask()
        }
        tvAutoButton = findViewById(R.id.tvAutoButton)
        tvAutoButton.setOnClickListener { showTvAutoSteps() }
        statClosed = findViewById(R.id.statClosed)
        statRam = findViewById(R.id.statRam)
        statCache = findViewById(R.id.statCache)
        statsText = findViewById(R.id.statsText)
        listHeader = findViewById(R.id.listHeader)
        listHint = findViewById(R.id.listHint)

        adapter = AppAdapter(onTap = { showOptions(it) }, onHold = { quickClose(it) }, onAutoStart = { setBlocked(it, !it.backgroundBlocked) })
        val list = findViewById<RecyclerView>(R.id.appList)
        val widthDp = resources.configuration.screenWidthDp
        val columns = when {
            widthDp >= 900 -> 3
            widthDp >= 600 -> 2
            else -> 1
        }
        list.layoutManager = GridLayoutManager(this, columns)
        list.adapter = adapter
        list.itemAnimator = null // smooth live updates, keeps TV remote focus in place

        sweepButton.setOnClickListener { sweepAll() }
        instantLink = findViewById(R.id.instantLink)
        instantLink.setOnClickListener {
            if (setupChoice == "pro" && Adb.hasWorkedBefore(this)) checkConnection(userAsked = true)
            else startInstantSetup()
        }
        applyHudTheme()

        Shell.readyListeners.add(onShellReady)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)

        updateMemory()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Gauge every 2 s, app list every 4 s — no refresh button needed
                var tick = 0
                while (true) {
                    updateMemory()
                    if (tick % 2 == 1 && ready && !busy) refresh(silent = true)
                    tick++
                    delay(2000)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { EasySweep.closedNow.collect { pkg -> removeFromList(pkg) } }
                EasySweep.state.collect { st ->
                    if (st.running) cleanGauge.setProgress(st.percent.toFloat())
                    val r = st.result
                    if (r != null) {
                        EasySweep.state.value = st.copy(result = null, report = "")
                        val gone = HashSet(EasySweep.closedPkgs)
                        if (gone.isNotEmpty()) showList(currentApps.filter { it.packageName !in gone })
                        cleanGauge.finish()
                        showSweepDone(r)
                        if (r.closed == 0 && st.report.isNotBlank()) showEasyReport(st.report)
                    }
                }
            }
        }
        autoSweepPending = intent?.getBooleanExtra("auto_sweep", false) == true

        // App Closer Pro: restore any past purchase and fetch the price
        Unlock.onUnlocked = {
            unlockDialog?.dismiss()
            toast("App Closer Pro unlocked ✓ Thank you!")
        }
        Unlock.connect(this)
        if (intent?.getBooleanExtra("show_unlock", false) == true) sweepButton.post { showUnlock() }
        if (intent?.getBooleanExtra("rapid_done", false) == true) sweepButton.postDelayed({ consumePendingRapid() }, 400)
        // Owner copies (not from the Play Store): tap the title 7 times to unlock / lock again
        findViewById<View>(R.id.appTitle)?.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - titleTapStart > 4000) { titleTapStart = now; titleTaps = 0 }
            if (++titleTaps >= 7) {
                titleTaps = 0
                when (Unlock.toggleOwnerUnlock(this)) {
                    true -> toast("Unlocked (test mode)")
                    false -> toast("Locked: paywall test mode. Tap 7 times again to unlock.")
                    null -> Unit
                }
            }
        }
        sweepButton.requestFocus() // TV remote starts on the big button
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("show_unlock", false)) showUnlock()
        if (intent.getBooleanExtra("rapid_done", false)) consumePendingRapid()
    }

    override fun onResume() {
        super.onResume()
        updateMemory()
        if (awaitingStep > 0 && (setupChoice == "easy" || pairHelper) && !SweepAccessibilityService.isEnabledInSettings(this)) {
            easyStep = awaitingStep
        }
        if (awaitingStep == 0 && setupChoice == "easy" && !pairHelper && SweepAccessibilityService.isEnabledInSettings(this)) {
            toast("Setup complete ✓")
        }
        awaitingStep = -1
        if (guidedActive) {
            onGuidedReturn()
            return
        }
        checkConnection(userAsked = false)
        if (ready && !busy) refresh()
    }

    override fun onDestroy() {
        Shell.readyListeners.remove(onShellReady)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        super.onDestroy()
    }

    // ---------- Connection + guided setup ----------

    private fun checkConnection(userAsked: Boolean) {
        if (Shell.isReady()) {
            onStatus(Shell.Status.READY)
            return
        }
        if (setupJob?.isActive == true) return
        if (isTv && !Adb.hasWorkedBefore(this) && !Shell.shizukuUsable()) {
            onStatus(Shell.Status.READY)   // guided or Easy mode, no setup screens on TV
            return
        }
        // Easy mode is on and Pro has never been set up: nothing to connect
        if (!userAsked && setupChoice != "pro" && SweepAccessibilityService.isOn() && !Adb.hasWorkedBefore(this) && !Shell.shizukuUsable()) {
            onStatus(Shell.Status.READY)
            return
        }
        if (userAsked) onStatus(Shell.Status.CONNECTING)
        setupJob = lifecycleScope.launch {
            var status = Shell.ensureConnected(applicationContext, userAsked)
            if (status != Shell.Status.READY && !userAsked && (SweepAccessibilityService.isOn() || isTv) &&
                (setupChoice != "pro" || Adb.hasWorkedBefore(applicationContext))) {
                status = Shell.Status.READY // fall back to Easy mode
            }
            onStatus(status)
        }
    }

    private fun onStatus(status: Shell.Status) {
        val wasReady = ready
        ready = status == Shell.Status.READY
        showStatus(status)
        if (ready && (!wasReady || currentApps.isEmpty())) refresh()
    }

    private fun showStatus(status: Shell.Status) {
        hideSetupExtras()
        resetStatusTextStyle()
        val isReady = status == Shell.Status.READY
        // Normal mode running: offer Instant mode (or reconnecting it, if it was set up before)
        instantLink.visibility = if (!isTv && isReady && !Shell.isReady()) View.VISIBLE else View.GONE
        instantLink.text = rapidLabel(setupChoice == "pro" && Adb.hasWorkedBefore(this))
        if (!busy) {
            sweepButton.text = sweepLabel()
            if (Themes.look.skin == null) setSweepLook(cleaning = false)
        }
        statusCard.visibility = if (isReady) View.GONE else View.VISIBLE
        sweepButton.isEnabled = isReady && !busy
        sweepButton.alpha = if (isReady) 1f else 0.4f
        statusButton.visibility = View.VISIBLE
        statusButton2.visibility = View.GONE
        codeRow.visibility = View.GONE
        shizukuButton.visibility = if (!isReady && Shell.shizukuInstalled(this)) View.VISIBLE else View.GONE

        linkButton.visibility = View.GONE
        // People who already set up Pro (before Easy existed) carry on with Pro
        val choice = setupChoice ?: if (Adb.hasWorkedBefore(this) || Adb.hasPaired(this)) "pro" else null
        if (!isReady && status != Shell.Status.CONNECTING && choice != "pro") {
            if (choice == "easy") showEasySetup() else showChoice()
            clearList()
            return
        }
        if (!isReady && status != Shell.Status.CONNECTING && pairHelper && !isTv) {
            if (SweepAccessibilityService.isOn()) {
                pairHelper = false          // step 1 done: on to step 2
            } else {
                showEasySetup()
                clearList()
                return
            }
        }
        if (!isReady) {
            linkButton.visibility = View.VISIBLE
            linkButton.text = if (SweepAccessibilityService.isOn()) "← Back to Basic Clean" else "← Back to choose your Clean"
            linkButton.setOnClickListener { leaveInstantSetup() }
        }

        when (status) {
            Shell.Status.NEED_DEV_OPTIONS -> {
                if (isTv) {
                    statusText.text = "One-time setup (about a minute)\n\n" +
                        "Step 1 of 2: turn on Developer options.\n" +
                        "Settings → System → About → click \"Android TV OS build\" 7 times, " +
                        "until it says you're a developer. Then come back here."
                    statusButton.text = "Open Settings"
                    statusButton.setOnClickListener { openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS) }
                    statusButton2.visibility = View.VISIBLE
                    statusButton2.text = "Next step →"
                    statusButton2.setOnClickListener { checkConnection(userAsked = false) }
                } else {
                    showRapidStep2(devOptionsOn = false)
                }
            }
            Shell.Status.NEED_DEBUGGING, Shell.Status.NEED_PAIRING -> {
                if (isTv) {
                    if (status == Shell.Status.NEED_DEBUGGING) {
                        statusText.text = "Step 2 of 2: turn on debugging.\n" +
                            "Settings → System → Developer options → switch on \"Network debugging\" " +
                            "(some TVs call it \"ADB debugging\"; if yours only has \"Wireless debugging\", switch that on).\n\n" +
                            "Then press Connect. If a box asks \"Allow debugging?\", tick \"Always allow\" and press OK."
                        statusButton.text = "Open Developer options"
                        statusButton.setOnClickListener { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }
                    } else {
                        statusText.text = "Step 2 of 2: pair the app once.\n" +
                            "Developer options → Wireless debugging → \"Pair device with pairing code\". " +
                            "Write down the 6-digit code, press HOME on the remote (not Back), " +
                            "open App Closer again, type the code below and press Pair."
                        codeRow.visibility = View.VISIBLE
                        statusButton.text = "Open Developer options"
                        statusButton.setOnClickListener { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }
                    }
                    statusButton2.visibility = View.VISIBLE
                    statusButton2.text = "Connect"
                    statusButton2.setOnClickListener { checkConnection(userAsked = true) }
                } else if (status == Shell.Status.NEED_DEBUGGING && Adb.hasPaired(this)) {
                    // already paired before: Wireless debugging just needs switching back on
                    statusText.text = "Wireless debugging is off. Android switches it off after a restart or a Wi-Fi change.\n\n" +
                        "Developer options → switch \"Wireless debugging\" back on (no new code needed), then press Connect."
                    statusButton.text = "Open Developer options"
                    statusButton.setOnClickListener { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }
                    statusButton2.visibility = View.VISIBLE
                    statusButton2.text = "Connect"
                    statusButton2.setOnClickListener { checkConnection(userAsked = true) }
                } else {
                    showRapidStep2(devOptionsOn = true)
                }
            }
            Shell.Status.CONNECTING -> {
                statusText.text = "Connecting…\n\nIf a box pops up asking \"Allow debugging?\", " +
                    "tick \"Always allow\" and press OK."
                statusButton.visibility = View.GONE
            }
            Shell.Status.READY -> Unit
        }
        if (!isReady) clearList()
    }

    private fun clearList() {
        currentApps = emptyList()
        adapter.items = emptyList()
        listHeader.text = "RUNNING APPS"
        statsText.text = ""
        appsCountText.text = "LAUNCHED APPS: –"
        if (statusButton.visibility == View.VISIBLE) statusButton.requestFocus()
    }

    /** First screen: Easy (one switch) or Pro (debugging). */
    /** Hides the parts of the card only some setup screens use. */
    private fun hideSetupExtras() {
        choiceBox.visibility = View.GONE
        stepImage.visibility = View.GONE
        statusText2.visibility = View.GONE
    }

    /** First screen after installing: Basic Clean or Rapid Total Clean. */
    private fun showChoice() {
        statusCard.visibility = View.VISIBLE
        hideSetupExtras()
        statusText.text = colored("Please select your Clean", 0)
        statusText.textSize = 20f
        statusText.gravity = android.view.Gravity.CENTER
        statusButton.visibility = View.GONE
        statusButton2.visibility = View.GONE
        codeRow.visibility = View.GONE
        linkButton.visibility = View.GONE
        choiceBox.visibility = View.VISIBLE

        val basic = findViewById<Button>(R.id.basicButton)
        val rapid = findViewById<SpeedButton>(R.id.rapidButton)
        basic.text = choiceLabel("BASIC CLEAN", "average user")
        rapid.text = choiceLabel("RAPID TOTAL CLEAN", "confident user")
        findViewById<TextView>(R.id.basicDesc).text =
            "Closes apps one by one through Android's own settings while the ram does the work. " +
                "Takes a few seconds per app. Quick setup, nothing technical."
        findViewById<TextView>(R.id.rapidDesc).text =
            "Closes every app in a couple of seconds, silently, and clears all caches instantly. " +
                "Uses Developer options and Wireless debugging. Save memory by turning off auto-start in selected apps. " +
                "More advanced setup, but a rapid, in-depth total clean."
        basic.setOnClickListener {
            setupChoice = "easy"
            easyStep = 1
            resetStatusTextStyle()
            showEasySetup()
            scrollToTop(statusCard)
        }
        rapid.setOnClickListener { showRapidIntro() }
        basic.requestFocus()
    }

    /** Plain-English explanation of what Rapid switches on, and why it's safe. */
    private fun rapidSafetyText(): CharSequence {
        val sb = android.text.SpannableStringBuilder()
        fun head(t: String) {
            val st = sb.length
            sb.append(t)
            sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), st, sb.length, 0)
            sb.setSpan(android.text.style.ForegroundColorSpan(Hud.CYAN), st, sb.length, 0)
        }
        head("What does Rapid switch on?\n")
        sb.append("Two hidden settings that are built into every Android phone: Developer options and Wireless debugging. " +
            "They let App Closer use the same commands Android's own Settings uses to close apps and clear caches, " +
            "so it can do everything at once in seconds.\n\n")
        head("Will it mess up my phone?\n")
        sb.append("No. Nothing is deleted, nothing is changed in your phone's system, and it doesn't root your phone or affect your warranty. " +
            "Switching Developer options on just shows an extra menu in Settings. Your phone works exactly the same.\n\n")
        head("Is it safe?\n")
        sb.append("Yes. Wireless debugging only works on your Wi-Fi, and only with apps you've paired using the 6-digit code. " +
            "Only App Closer is paired, so nothing else can connect.\n\n")
        head("Can I undo it?\n")
        sb.append("Any time: Settings → System → Developer options → switch it off at the top. " +
            "App Closer then simply uses Basic Clean.\n\n")
        head("Good to know\n")
        sb.append("Android switches Wireless debugging off after a restart or a Wi-Fi change; App Closer switches it back on for you. " +
            "A few banking apps don't like Developer options being on: if one complains, switch Developer options off while you bank.")
        return sb
    }

    /** "Is this safe?" from inside the Rapid setup steps. */
    private fun showRapidExplainer() {
        AlertDialog.Builder(this)
            .setTitle("About Rapid Total Clean")
            .setMessage(rapidSafetyText())
            .setPositiveButton("OK", null)
            .show()
    }

    /** Before Rapid setup starts: what it uses, in plain words, then a clear choice. */
    private fun showRapidIntro() {
        statusCard.visibility = View.VISIBLE
        hideSetupExtras()
        resetStatusTextStyle()
        statusText.text = android.text.SpannableStringBuilder("RAPID TOTAL CLEAN · BEFORE YOU START\n\n").append(rapidSafetyText())
        statusButton.visibility = View.GONE
        statusButton2.visibility = View.VISIBLE
        statusButton2.text = "OK, start the setup →"
        statusButton2.setOnClickListener { startRapidSetup() }
        linkButton.visibility = View.VISIBLE
        linkButton.text = "← No thanks, back to choose your Clean"
        linkButton.setOnClickListener {
            setupChoice = null
            showChoice()
        }
        statusButton2.requestFocus()
        scrollToTop(statusCard)
    }

    private fun startRapidSetup() {
        run {
            setupChoice = "pro"
            resetStatusTextStyle()
            hideSetupExtras()
            if (!isTv && !SweepAccessibilityService.isOn()) {
                // Rapid step 1 of 2: allow App Closer (it needs this to pair by itself)
                pairHelper = true
                easyStep = 1
                showEasySetup()
            } else {
                checkConnection(userAsked = false)
            }
            scrollToTop(statusCard)
        }
    }

    private fun resetStatusTextStyle() {
        statusText.textSize = 15f
        statusText.gravity = android.view.Gravity.START
    }

    /** Big bold name, small dim "(who it's for)" underneath. */
    private fun choiceLabel(name: String, who: String): CharSequence {
        val sb = android.text.SpannableStringBuilder()
        sb.append(name, android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0)
        sb.setSpan(android.text.style.RelativeSizeSpan(1.25f), 0, name.length, 0)
        val s0 = sb.length
        sb.append("\n($who)")
        sb.setSpan(android.text.style.RelativeSizeSpan(0.85f), s0, sb.length, 0)
        sb.setSpan(android.text.style.ForegroundColorSpan(getColor(R.color.text_dim)), s0, sb.length, 0)
        return sb
    }

    /**
     * Builds step text where some words are buttons: pass plain strings, and
     * Pair("Accessibility") { … } for a tappable word drawn as a pill.
     */
    private fun stepText(vararg parts: Any): CharSequence {
        val sb = android.text.SpannableStringBuilder()
        for (part in parts) {
            if (part is Pair<*, *>) {
                val word = part.first as String
                @Suppress("UNCHECKED_CAST") val action = part.second as () -> Unit
                val st = sb.length
                sb.append(word)
                sb.setSpan(PillSpan(getColor(R.color.accent_red), android.graphics.Color.WHITE, resources.displayMetrics.density),
                    st, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(object : android.text.style.ClickableSpan() {
                    override fun onClick(widget: View) = action()
                    override fun updateDrawState(ds: android.text.TextPaint) {}
                }, st, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                sb.append(part.toString())
            }
        }
        return sb
    }

    /** Easy setup: switch on the accessibility service. */
    /**
     * Android 13+ blocks Accessibility for apps installed from a link until "Allow restricted
     * settings" is turned on. true = allowed, false = still blocked, null = can't tell on this phone.
     */
    private fun restrictedSettingsAllowed(): Boolean? {
        if (isTv || Build.VERSION.SDK_INT < 33) return true
        if (SweepAccessibilityService.isEnabledInSettings(this)) return true
        return try {
            val ops = getSystemService(android.app.AppOpsManager::class.java)
            val mode = ops.unsafeCheckOpNoThrow(
                "android:access_restricted_settings", applicationInfo.uid, packageName
            )
            mode != android.app.AppOpsManager.MODE_ERRORED && mode != android.app.AppOpsManager.MODE_IGNORED
        } catch (_: Exception) {
            null
        }
    }

    /** Pro setup is switching on Accessibility so it can find and enter the pairing code itself. */
    private var pairHelper: Boolean
        get() = prefs.getBoolean("pair_helper", false)
        set(v) { prefs.edit().putBoolean("pair_helper", v).apply() }

    private var easyStep: Int
        get() = prefs.getInt("easy_step", 1)
        set(v) { prefs.edit().putInt("easy_step", v).apply() }

    private fun goEasyStep(step: Int) {
        easyStep = step
        showEasySetup()
    }

    /**
     * Easy setup, one step at a time. On Android 13+ Android only offers "Allow restricted
     * settings" (the ⋮ menu) AFTER you've tried to switch the app on once, so the steps follow
     * that order: try → unlock → switch on.
     */
    private fun showEasySetup() {
        val title = if (pairHelper) "RAPID TOTAL CLEAN SETUP · STEP 1 OF 2\nAllow App Closer" else "BASIC CLEAN SETUP"
        val word = if (pairHelper) "PART" else "STEP"
        statusCard.visibility = View.VISIBLE
        hideSetupExtras()
        resetStatusTextStyle()
        codeRow.visibility = View.GONE
        statusButton.visibility = View.VISIBLE
        statusButton2.visibility = View.GONE
        linkButton.visibility = View.VISIBLE

        if (SweepAccessibilityService.isEnabledInSettings(this) && !SweepAccessibilityService.isOn()) {
            statusText.text = (if (pairHelper) "Switched on ✓ Starting automatic pairing…\n\n" else "Nearly there… starting up.\n\n") +
                "If this doesn't change in a few seconds, switch App Closer off and on again in Accessibility."
            statusButton.visibility = View.GONE
            linkButton.visibility = View.GONE
            statusCard.postDelayed({ if (!isFinishing) checkConnection(userAsked = false) }, 1500)
            return
        }

        val needsUnlock = !isTv && Build.VERSION.SDK_INT >= 33
        if (needsUnlock && restrictedSettingsAllowed() == true && easyStep < 3) easyStep = 3
        val step = if (needsUnlock) easyStep.coerceIn(1, 3) else 3

        val openAccessibility: () -> Unit = {
            awaitingStep = if (step == 1) 2 else 0
            openAccessibilitySettings()
        }
        when (step) {
            1 -> {
                statusText.text = stepText(
                    "$title · $word 1 OF 3\n\n",
                    "1. Tap ", Pair("  Accessibility  ", openAccessibility), "\n",
                    "2. Tap \"App Closer\" even though it is greyed out. It looks like this:"
                )
                showStepPicture(icon = true, title = "App Closer", sub = "Controlled by Restricted Setting", hint = "👆")
                statusText2.visibility = View.VISIBLE
                statusText2.text = "Tap it anyway. Access will be denied: tap Close.\n\n" +
                    "Press Back to return here."
                statusButton.visibility = View.GONE
                statusButton2.visibility = View.VISIBLE
                statusButton2.text = "Next step →"
                statusButton2.setOnClickListener { goEasyStep(2) }
                backToChoiceOrPairing()
            }
            2 -> {
                statusText.text = stepText(
                    "$title · $word 2 OF 3\n\n",
                    "1. Tap ", Pair("  Open app info  ", {
                        awaitingStep = 3
                        openSettingsUri(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName")
                    }), "\n",
                    "2. Tap the 3 dots ⋮ in the top right corner.\n",
                    "3. Tap \"Allow restricted settings\".\n",
                    "4. Enter your PIN or fingerprint if asked.\n\n",
                    "Press Back to return here."
                )
                statusButton.visibility = View.GONE
                statusButton2.visibility = View.VISIBLE
                statusButton2.text = "Next step →"
                statusButton2.setOnClickListener { goEasyStep(3) }
                linkButton.text = "← Back to step 1"
                linkButton.setOnClickListener { goEasyStep(1) }
            }
            else -> {
                statusText.text = stepText(
                    if (needsUnlock) "$title · $word 3 OF 3\n\n" else "$title\n\n",
                    "1. Tap ", Pair("  Accessibility  ", openAccessibility), if (needsUnlock) " again\n" else "\n",
                    if (isTv) "2. Choose App Closer (under Services or Downloaded services).\n3. Switch it on and choose OK.\n"
                    else "2. Tap App Closer (under Downloaded apps).\n3. Turn on \"Use App Closer\" and tap Allow.\n",
                    if (pairHelper) "4. Press Back to return here for step 2."
                    else "4. Press Back to return here. Setup is then complete."
                )
                statusButton.visibility = View.GONE
                if (needsUnlock) {
                    linkButton.text = "← Back to step 2"
                    linkButton.setOnClickListener { goEasyStep(2) }
                } else {
                    backToChoiceOrPairing()
                }
            }
        }
        statusButton2.requestFocus()
    }

    private fun backToChoiceOrPairing() {
        if (pairHelper) {
            linkButton.text = "← Back to choose your Clean"
            linkButton.setOnClickListener {
                pairHelper = false
                setupChoice = null
                showChoice()
            }
        } else {
            linkButton.visibility = View.VISIBLE
            linkButton.text = "← Back to choose your Clean"
            linkButton.setOnClickListener {
                setupChoice = null
                showChoice()
            }
        }
    }



    private fun openSettingsUri(action: String, uri: String) {
        try {
            startActivity(Intent(action, Uri.parse(uri)))
        } catch (_: ActivityNotFoundException) {
            openSettings(Settings.ACTION_SETTINGS)
        }
    }

    /**
     * Phones: if App Closer's Accessibility switch is on, it can open Wireless debugging, read the
     * 6-digit code and pair by itself.
     */
    /**
     * Rapid step 2 of 2, kept as simple as the Accessibility steps: switch on Developer options
     * (only if they're off), then one tap to pair.
     */
    private fun showRapidStep2(devOptionsOn: Boolean) {
        val pairWord = Pair("  Pair automatically  ", { startAutoPair() })
        val warn = "\n\n✋ DON'T TOUCH THE SCREEN while it pairs (about 10 seconds). App Closer does all the tapping."
        val text = if (devOptionsOn) {
            stepText(
                "RAPID TOTAL CLEAN SETUP · STEP 2 OF 2\n",
                if (SweepAccessibilityService.isOn()) "Step 1 done ✓\n" else "",
                "PART 2 OF 2 · Pair App Closer   ", Pair(" Is this safe? ", { showRapidExplainer() }), "\n\n",
                "1. Make sure you're on Wi-Fi.\n",
                "2. Tap ", pairWord, warn
            )
        } else {
            showDeveloperOptionsPart()
            return
        }
        // the warning in bold red
        val sb = android.text.SpannableStringBuilder(text)
        val at = sb.indexOf("✋")
        if (at >= 0) {
            sb.setSpan(android.text.style.ForegroundColorSpan(getColor(R.color.accent_red)), at, sb.length, 0)
            sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, sb.length, 0)
        }
        statusText.text = sb
        statusButton.visibility = View.GONE
        statusButton2.visibility = View.VISIBLE
        statusButton2.text = "Enter the code myself instead"
        statusButton2.setOnClickListener { startPhonePairing() }
        codeRow.visibility = View.GONE
    }

    private fun developerOptionsOn(): Boolean = Adb.developerOptionsOn(this)

    /** The picture of a Settings row under a step (App Closer greyed out, or Build number). */
    private fun showStepPicture(icon: Boolean, title: String, sub: String, hint: String) {
        stepImage.visibility = View.VISIBLE
        findViewById<View>(R.id.stepImageIcon).visibility = if (icon) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.stepImageTitle).text = title
        findViewById<TextView>(R.id.stepImageSub).text = sub
        findViewById<TextView>(R.id.stepImageHint).text = hint
    }

    /**
     * Rapid step 2, part 1: switch on Developer options, as simply as the Accessibility steps.
     * One button opens the right page, a picture shows what to tap, and it moves on by itself.
     */
    private fun showDeveloperOptionsPart() {
        val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        val xiaomi = Build.MANUFACTURER.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }
        val rowName = if (xiaomi) "OS version" else "Build number"
        statusText.text = stepText(
            "RAPID TOTAL CLEAN SETUP · STEP 2 OF 2\n",
            if (SweepAccessibilityService.isOn()) "Step 1 done ✓\n" else "",
            "PART 1 OF 2 · Switch on Developer options   ", Pair(" Is this safe? ", { showRapidExplainer() }), "\n\n",
            "1. Tap ", Pair("  About phone  ", { openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS) }), "\n",
            if (samsung) "2. Tap \"Software information\", then find \"$rowName\". It looks like this:"
            else "2. Scroll to the bottom and find \"$rowName\". It looks like this:"
        )
        showStepPicture(icon = false, title = rowName, sub = Build.DISPLAY, hint = "👆 ×7")
        statusText2.visibility = View.VISIBLE
        statusText2.text = "3. Tap it 7 times, quickly, until it says \"You are now a developer!\"\n" +
            "4. If it asks for your PIN, enter it.\n\n" +
            "Press Back to return here."
        statusButton.visibility = View.GONE
        statusButton2.visibility = View.VISIBLE
        statusButton2.text = "Next step →"
        statusButton2.setOnClickListener {
            if (developerOptionsOn()) checkConnection(userAsked = false)
            else toast("Developer options aren't on yet: tap $rowName 7 times")
        }
        codeRow.visibility = View.GONE
        // still offer the hands-free way, quietly
        linkButton.visibility = View.VISIBLE
        linkButton.text = "Or let App Closer tap it for me"
        linkButton.setOnClickListener { startAutoDeveloperOptions() }
    }

    /** App Closer taps "Build number" 7 times for the user. */
    private fun startAutoDeveloperOptions() {
        if (developerOptionsOn()) {
            toast("Developer options are already on ✓")
            checkConnection(userAsked = false)
            return
        }
        if (!SweepAccessibilityService.isOn()) {
            // it needs App Closer allowed in Accessibility to do the tapping: step 1 first
            pairHelper = true
            easyStep = 1
            showEasySetup()
            scrollToTop(statusCard)
            return
        }
        val svc = SweepAccessibilityService.instance ?: return
        toast("Tapping Build number for you… enter your PIN if asked")
        val started = svc.autoDeveloperOptions { error ->
            if (error == null) toast("Developer options are on ✓ Now tap Pair automatically")
            else toast(error)
            checkConnection(userAsked = false)
        }
        if (!started) toast("App Closer is busy. Try again in a moment.")
    }

    /** Finds the code and pairs, with App Closer doing the tapping. */
    private fun startAutoPair() {
        if (!developerOptionsOn()) {
            toast("Switch on Developer options first (step 1 above)")
            return
        }
        if (!SweepAccessibilityService.isOn()) {
            // it needs App Closer allowed in Accessibility to read the code: do step 1 first
            pairHelper = true
            easyStep = 1
            showEasySetup()
            scrollToTop(statusCard)
            return
        }
        val svc = SweepAccessibilityService.instance ?: return
        val started = svc.autoPair { error ->
            if (error == null) {
                toast("Paired ✓ Connecting…")
                checkConnection(userAsked = true)
            } else {
                toast(error)
            }
        }
        if (!started) toast("App Closer is busy. Try again in a moment.")
    }

    private fun startPhonePairing() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFY_REQUEST)
            return
        }
        PairingNotification.showCodeBox(applicationContext)
        openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        toast("Now tap Wireless debugging → Pair device with pairing code")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFY_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startPhonePairing()
            } else {
                toast("No problem. Type the code into the box here instead (use split screen if you can).")
            }
        }
    }

    /** Pairing with the code typed into the app itself (TV, or phone in split screen). */
    private fun pairFromApp() {
        val code = codeInput.text.toString()
        pairButton.isEnabled = false
        pairButton.text = "Pairing…"
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) { Adb.pair(applicationContext, code) }
            pairButton.isEnabled = true
            pairButton.text = "Pair"
            if (error == null) {
                toast("Paired ✓ Connecting…")
                checkConnection(userAsked = true)
            } else {
                toast(error)
            }
        }
    }

    private fun useShizuku() {
        when {
            !Shell.shizukuRunning() -> openShizuku()
            !Shell.shizukuUsable() -> try {
                Shizuku.requestPermission(PERMISSION_REQUEST)
            } catch (_: Throwable) {
                openShizuku()
            }
            else -> checkConnection(userAsked = false)
        }
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                toast("Open the Settings app")
            }
        }
    }

    // ---------- Scanning ----------

    private fun refresh(silent: Boolean = false) {
        if (!ready) return
        if (silent && scanJob?.isActive == true) return
        scanJob?.cancel()
        scanJob = lifecycleScope.launch {
            if (currentApps.isEmpty()) listHeader.text = "SCANNING…"
            val easy = easyMode
            val guided = tvGuided
            val tvLaunched = tvList
            val apps = withContext(Dispatchers.IO) {
                if (tvLaunched) {
                    sweeper.scanTvLaunched()
                } else if (easy) {
                    sweeper.scanEasy()
                } else {
                    if (!usageGranted) {
                        sweeper.grantUsageAccess()
                        usageGranted = true
                    }
                    sweeper.scan(includeSystem = false)
                }
            }
            adapter.easyMode = easy || tvLaunched
            showList(apps)
            if (autoSweepPending && !busy) {
                autoSweepPending = false
                sweepAll()
            }
            updateMemory()
            if (guided) {
                statsText.text = "Press Close All: each app's page opens. Press Force stop, OK, then Clear cache, then Back."
                statsText.visibility = View.VISIBLE
                return@launch
            }
            if (tvLaunched) {
                statsText.text = "Automatic closing is on · press Close All"
                statsText.visibility = View.VISIBLE
                return@launch
            }
            if (easy) {
                statsText.text = ""
                statsText.visibility = View.GONE     // the list header already explains the list
                return@launch
            }
            // Total cache across all installed apps takes a moment, so only work it out
            // on a full refresh (start-up, after a sweep), not on the quiet 6-second updates
            // ...and do it on its own, so it never holds up the next list update
            updateStats(apps, lastTotalCache)
            if ((!silent || lastTotalCache < 0) && cacheJob?.isActive != true) {
                cacheJob = lifecycleScope.launch {
                    lastTotalCache = withContext(Dispatchers.IO) { sweeper.totalCache() }
                    updateStats(currentApps, lastTotalCache)
                }
            }
        }
    }

    private fun showList(apps: List<RunningApp>) {
        currentApps = if (easyMode) {
            apps.sortedByDescending { it.openedAt }          // most recently opened first
        } else {
            apps.sortedWith(compareBy<RunningApp> { it.isParked }.thenByDescending { it.memoryKb })   // active first, then parked; biggest RAM users first
        }
        adapter.items = currentApps
        // only apps that are really active count; parked (cached) ones are listed greyed out underneath
        val userApps = currentApps.count { !it.isSystem && it.isRunning && !it.isParked }
        appsCountText.text = colored("LAUNCHED APPS: ", 0, "$userApps", Hud.CYAN)
        tvAutoButton.visibility = if (tvGuided && !busy) View.VISIBLE else View.GONE
        if (tvList) {
            listHeader.text = colored("LAUNCHED APPS ", 0, "[ $userApps ]", Hud.CYAN)
            listHint.text = if (userApps == 0) "No launched apps. All clean!"
                else "Select an app to close just that one"
        } else if (easyMode) {
            listHeader.text = colored("OPENED APPS ", 0, "[ $userApps ]", Hud.CYAN)
            listHint.text = if (userApps == 0) {
                "Nothing open right now. Apps you open will show up here, ready for your next sweep."
            } else {
                "Most recently opened first · Tap an app for options · Hold to close it"
            }
        } else {
            listHeader.text = colored("ACTIVE APPS ", 0, "[ $userApps ]", Hud.CYAN)
            val parked = currentApps.count { !it.isSystem && it.isRunning && it.isParked }
            val parkedNote = if (parked > 0)
                "\nGreyed out: $parked parked by Android (paused, no battery use). Other apps and notifications can wake apps briefly; Android parks them again."
            else ""
            listHint.text = (if (userApps == 0) {
                "No apps active in the background. All clean!"
            } else {
                "Active apps first, biggest RAM users first · Tap an app for options · Hold to close it"
            }) + parkedNote
        }
    }

    private fun updateStats(apps: List<RunningApp>, totalCache: Long) {
        val appRam = apps.filter { !it.isSystem }.sumOf { it.memoryKb }
        val parts = mutableListOf("Apps using ${AppAdapter.formatKb(appRam)} RAM")
        if (totalCache >= 0) parts.add("${AppAdapter.formatBytes(totalCache)} cache can be cleared")
        statsText.text = parts.joinToString("  ·  ")
        statsText.visibility = View.VISIBLE
    }

    // ---------- Actions ----------

    private fun sweepAll() {
        if (busy || !ready) return
        val rapidNow = Shell.isReady()
        // Basic: 5 free Close All cleans. Rapid: 3 free tries. Then Pro.
        if (if (rapidNow) !Unlock.canRapid(this) else !Unlock.canClean(this)) {
            showUnlock()
            return
        }
        val apps = currentApps
        if (tvGuided) {
            val targets = apps.filter { it.isRunning && !it.isProtected }
            if (targets.isNotEmpty()) countFreeClean()
            startGuided(targets)
            return
        }
        if (easyMode) {
            val svc = SweepAccessibilityService.instance ?: return
            val targets = apps.filter { it.isRunning && !it.isProtected }
            if (targets.isEmpty()) {
                toast("Nothing to close yet. Apps you open will show up here.")
                return
            }
            countFreeClean()
            startSweepUi()
            if (!svc.sweep(targets.map { it.packageName to it.label }, clearRecents = Prefs.clearRecents(this))) endSweepUi()
            return
        }
        countFreeRapid()
        startSweepUi()
        lifecycleScope.launch {
            val clearRecents = Prefs.clearRecents(this@MainActivity)
            val t0 = android.os.SystemClock.elapsedRealtime()
            var cleanSecs = 0f
            val result = withContext(Dispatchers.IO) {
                sweeper.sweepAll(
                    apps,
                    onProgress = { pct ->
                        // time to close everything and clear caches (not the wait before measuring RAM)
                        if (pct >= 90 && cleanSecs == 0f) cleanSecs = (android.os.SystemClock.elapsedRealtime() - t0) / 1000f
                        runOnUiThread { cleanGauge.setProgress(pct.toFloat()) }
                    },
                    onClosed = { pkg -> runOnUiThread { removeFromList(pkg) } },
                    clearRecents = false     // done last, below: wiping Recents also closes App Closer's own window
                )
            }
            cleanGauge.finish()
            if (cleanSecs == 0f) cleanSecs = (android.os.SystemClock.elapsedRealtime() - t0) / 1000f
            if (clearRecents) {
                // Save the result, then wipe Recents and reopen App Closer from the system side,
                // so the results and the ALL CLEAN ram still show even though our own window was closed.
                savePendingRapid(result, cleanSecs)
                withContext(Dispatchers.IO) {
                    Shell.exec(
                        "nohup sh -c 'sleep 0.4; am clear-recent-apps; sleep 0.6; " +
                            "am start -n $packageName/.MainActivity --ez rapid_done true' >/dev/null 2>&1 &"
                    )
                }
                // if App Closer's window survived the wipe, show it here instead
                kotlinx.coroutines.delay(3500)
                consumePendingRapid()
            } else {
                showSweepDone(result)
                showRapidAllClean(cleanSecs)
            }
        }
    }

    // ---------- Guided closing (TV, no setup) ----------

    private fun startGuided(targets: List<RunningApp>) {
        if (targets.isEmpty()) {
            toast("Nothing to close. All clean!")
            return
        }
        startSweepUi()
        guidedActive = true
        guidedQueue.clear()
        guidedQueue.addAll(targets)
        guidedTotal = targets.size
        guidedClosed = 0
        guidedRamBefore = sweeper.availableRam()
        guidedDiskBefore = sweeper.freeStorage()
        sweepButton.isEnabled = true
        sweepButton.text = "STOP"
        sweepButton.setOnClickListener { finishGuided() }
        openNextGuided()
    }

    private fun openNextGuided() {
        val app = guidedQueue.removeFirstOrNull()
        if (app == null) {
            finishGuided()
            return
        }
        guidedCurrent = app
        val n = guidedTotal - guidedQueue.size
        cleanGauge.setProgress(100f * (n - 1) / guidedTotal)
        resultText.text = "Closing $n of $guidedTotal: ${app.label}"
        resultText.visibility = View.VISIBLE
        Toast.makeText(this, "${app.label} ($n of $guidedTotal): press Force stop, OK, then Clear cache, then Back", Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
        } catch (_: ActivityNotFoundException) {
            openNextGuided()
        }
    }

    /** Drops an app from the list the moment it's closed. */
    private fun removeFromList(pkg: String) {
        if (currentApps.none { it.packageName == pkg }) return
        showList(currentApps.filter { it.packageName != pkg })
    }

    /** Back from an app's page: count it if it was stopped, then open the next one after a moment. */
    private fun onGuidedReturn() {
        guidedCurrent?.let {
            if (sweeper.isStopped(it.packageName)) {
                guidedClosed++
                removeFromList(it.packageName)
            }
        }
        guidedCurrent = null
        val n = guidedTotal - guidedQueue.size
        cleanGauge.setProgress(100f * n / guidedTotal)
        if (guidedQueue.isEmpty()) {
            finishGuided()
            return
        }
        resultText.text = "Next: ${guidedQueue.first().label}  ·  press STOP to finish now"
        resultText.visibility = View.VISIBLE
        sweepButton.requestFocus()
        guidedNextJob?.cancel()
        guidedNextJob = lifecycleScope.launch {
            delay(1800)
            if (guidedActive) openNextGuided()
        }
    }

    private fun finishGuided() {
        if (!guidedActive) return
        guidedActive = false
        guidedNextJob?.cancel()
        guidedCurrent?.let {
            if (sweeper.isStopped(it.packageName)) {
                guidedClosed++
                removeFromList(it.packageName)
            }
        }
        guidedCurrent = null
        guidedQueue.clear()
        sweepButton.setOnClickListener { sweepAll() }
        sweepButton.isEnabled = false
        sweepButton.text = "FINISHING…"
        resultText.text = "Letting Android settle before measuring…"
        lifecycleScope.launch {
            delay(3000)   // give Android a moment to reclaim the memory before the "after" reading
            val result = SweepResult(
                closed = guidedClosed,
                ramFreed = (sweeper.availableRam() - guidedRamBefore).coerceAtLeast(0),
                cacheFreed = (sweeper.freeStorage() - guidedDiskBefore).coerceAtLeast(0)
            )
            cleanGauge.finish()
            showSweepDone(result)
        }
    }

    /** TV: how to switch on automatic closing (one time). */
    private fun showTvAutoSteps() {
        AlertDialog.Builder(this)
            .setTitle("Make closing automatic")
            .setMessage(
                "App Closer can press Force stop, OK and Clear cache on each app for you, and go back by itself.\n\n" +
                    "Switch it on once:\n" +
                    "1. Select \"Open Accessibility\" below.\n" +
                    "2. Scroll to the bottom of the list and select App Closer.\n" +
                    "3. Switch it on and choose OK.\n" +
                    "4. Press Back on the remote to return here.\n\n" +
                    "You can switch it off again in the same place."
            )
            .setPositiveButton("Open Accessibility") { _, _ -> openAccessibilitySettings() }
            .setNegativeButton("Not now", null)
            .show()
    }

    /**
     * The real Accessibility screen inside the device's own Settings app. Needed on some TVs and
     * projectors where the standard "open Accessibility" request goes nowhere.
     */
    private fun findAccessibilityScreen(): Intent? {
        val pkgs = linkedSetOf("com.android.tv.settings", "com.google.android.tv.settings", "com.android.settings")
        packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName?.let { pkgs.add(it) }
        val found = ArrayList<android.content.pm.ActivityInfo>()
        for (pkg in pkgs) {
            val acts = try {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities
            } catch (_: Exception) {
                null
            } ?: continue
            acts.filterTo(found) { it.exported && it.enabled && it.name.contains("ccessibility") }
        }
        // the main Accessibility page, not a sub-page
        val best = found.firstOrNull { it.name.endsWith("AccessibilityActivity") || it.name.endsWith("AccessibilitySettingsActivity") }
            ?: found.firstOrNull { it.name.endsWith("ccessibilitySettings") }
            ?: found.firstOrNull()
        return best?.let { Intent().setClassName(it.packageName, it.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    private fun openAccessibilitySettings() {
        val standard = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val direct = findAccessibilityScreen()
        // TVs: go straight to the real screen first. Phones: the standard request works fine.
        val order = if (isTv) listOfNotNull(direct, standard) else listOfNotNull(standard, direct)
        for (intent in order) {
            val target = packageManager.resolveActivity(intent, 0)?.activityInfo ?: continue
            if (!target.exported || !target.enabled || target.packageName == "android") continue
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        // Last resort: the main Settings screen, with a pointer
        openSettings(Settings.ACTION_SETTINGS)
        toast(
            if (isTv) "Select Accessibility, then App Closer at the bottom"
            else "Tap Accessibility, then App Closer"
        )
    }

    private fun startSweepUi() {
        busy = true
        tvAutoButton.visibility = View.GONE
        allCleanView.animate().cancel()
        allCleanView.visibility = View.GONE
        sweepButton.visibility = View.VISIBLE
        sweepButton.isEnabled = false
        setSweepLook(cleaning = true)   // green while it cleans
        sweepButton.text = "[ CLEANING… ]"
        resultText.visibility = View.GONE
        resultRow.visibility = View.GONE
        cleanGauge.startCleaning()
    }

    private fun endSweepUi() {
        busy = false
        sweepButton.isEnabled = true
        setSweepLook(cleaning = false)
        sweepButton.text = sweepLabel()
        cleanGauge.reset()
    }

    /** Easy mode couldn't press the buttons on this phone: show what it saw so it can be fixed. */
    private fun showEasyReport(report: String) {
        AlertDialog.Builder(this)
            .setTitle("Easy mode needs a tweak for this phone")
            .setMessage(
                "It couldn't find or press the Force stop buttons on this phone's Settings screens. " +
                    "Copy this report and send it to the developer so it can be fixed:\n\n" + report.take(1500)
            )
            .setPositiveButton("Copy report") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Easy mode report", report))
                toast("Report copied")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /** Green button + "Closed 6 apps · freed …", then back to normal after 5 seconds. */
    private fun showSweepDone(result: SweepResult) {
        Unlock.addTotals(this, result.closed, result.cacheFreed)
        statClosed.text = bracketed(String.format(java.util.Locale.UK, "%02d", result.closed), Hud.BLUE)
        statRam.text = bracketed(AppAdapter.formatBytes(result.ramFreed), Hud.GREEN)
        statCache.text = bracketed(AppAdapter.formatBytes(result.cacheFreed), Hud.PURPLE)
        resultRow.visibility = View.VISIBLE
        busy = false
        sweepButton.isEnabled = true
        val remaining = currentApps.count { it.isRunning && !it.isSystem && !it.isProtected }
        val worked = (result.closed > 0 || result.cacheFreed > 0) && remaining == 0
        sweptThisSession = true
        if (worked) {
            // The happy ram pops in where the button was
            resultText.visibility = View.GONE
            sweepButton.visibility = View.INVISIBLE   // stays in place for when it comes back
            allCleanView.visibility = View.VISIBLE
            allCleanView.scaleX = 0.6f
            allCleanView.scaleY = 0.6f
            allCleanView.alpha = 0f
            allCleanView.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(450).setInterpolator(android.view.animation.OvershootInterpolator(1.6f)).start()
        } else if (result.closed > 0) {
            // closed some (e.g. a single app) but others are still open: no "All clean!"
            resultText.visibility = View.GONE
            setSweepLook(cleaning = false)
            sweepButton.text = sweepLabel()
            cleanGauge.reset()
        } else {
            resultText.text = if (isTv && !Shell.isReady() && !SweepAccessibilityService.isOn()) {
                "Nothing was closed. On each app's page, press Force stop, then OK, then Clear cache."
            } else if (easyMode) {
                "Nothing was closed. Easy mode couldn't press the buttons on this phone (see the report)."
            } else {
                "Nothing needed closing. Everything was already clean."
            }
            resultText.visibility = View.VISIBLE
            setSweepLook(cleaning = false)
            sweepButton.text = sweepLabel()
            cleanGauge.reset()
        }
        refresh()
        lifecycleScope.launch {
            delay(15000)   // the happy ram stays up for 15 seconds
            if (!busy) {
                showSweepButton()
                cleanGauge.reset()
            }
        }
    }

    /** Back to the normal pink button (after the happy ram has had its moment). */
    private fun showSweepButton() {
        setSweepLook(cleaning = false)
        sweepButton.text = sweepLabel()
        if (allCleanView.visibility == View.VISIBLE) {
            allCleanView.animate().alpha(0f).setDuration(250).withEndAction {
                allCleanView.visibility = View.GONE
                sweepButton.visibility = View.VISIBLE
                sweepButton.requestFocus()
            }.start()
        } else {
            sweepButton.visibility = View.VISIBLE
        }
    }


    /** ⚡ Rapid Total Clean: Developer options + pairing. Free to set up and try; then part of Pro. */
    private fun startInstantSetup() {
        showRapidIntro()
    }

    private fun scrollToTop(v: View) {
        var p: View? = v
        while (p != null && p !is androidx.core.widget.NestedScrollView) p = p.parent as? View
        (p as? androidx.core.widget.NestedScrollView)?.smoothScrollTo(0, 0)
    }

    private fun leaveInstantSetup() {
        pairHelper = false
        if (SweepAccessibilityService.isOn()) {
            setupChoice = "easy"
            checkConnection(userAsked = false)
        } else {
            setupChoice = null
            hideSetupExtras()
            showChoice()
        }
    }

    /** The sci-fi HUD look: cut-corner neon panels and buttons. */
    private fun applyHudTheme() {
        val look = Themes.load(this)
        findViewById<View>(R.id.mainScroll)?.setBackgroundResource(look.background)
        findViewById<TextView>(R.id.appTitle)?.apply {
            setTextColor(look.edge)
            setShadowLayer(14f * resources.displayMetrics.density / 2, 0f, 0f, (look.edge and 0x00FFFFFF) or 0xAA000000.toInt())
        }
        appsCountText.setTextColor(0xFFE6F7FF.toInt())
        // Themes are parked for now: hidden until the cleaning itself is fully tested
        findViewById<Button>(R.id.themeButton)?.visibility = if (Themes.ENABLED) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.themeButton)?.apply {
            background = HudDrawable(resources.displayMetrics.density, look.edge,
                0xFF0B1A28.toInt(), 0xFF081320.toInt(), cut = 7f, glow = false, accents = false)
            setOnClickListener { showThemes() }
        }
        findViewById<View>(R.id.ramPanel)?.background = Hud.panel(this)
        statusCard.background = Hud.panel(this)
        findViewById<View>(R.id.listPanel)?.background = Hud.panel(this)
        ramFreeText.background = HudDrawable(resources.displayMetrics.density, Hud.GREEN,
            0xFF0B2A1C.toInt(), 0xFF081E14.toInt(), cut = 7f, glow = false, accents = false)
        setSweepLook(cleaning = false)
        closeX?.background = HudDrawable(resources.displayMetrics.density, Hud.RED,
            0xFF3A0C12.toInt(), 0xFF24070B.toInt(), cut = 7f, glow = false, accents = false)
        sweepButton.text = sweepLabel()
        for (id in intArrayOf(R.id.basicButton, R.id.rapidButton, R.id.statusButton, R.id.statusButton2,
            R.id.linkButton, R.id.pairButton, R.id.tvAutoButton)) {
            findViewById<View>(id)?.background = Hud.button(this, Hud.CYAN)
        }
        (statClosed.parent as? View)?.background = Hud.statBox(this, Hud.BLUE)
        (statRam.parent as? View)?.background = Hud.statBox(this, Hud.GREEN)
        (statCache.parent as? View)?.background = Hud.statBox(this, Hud.PURPLE)
        // the upgrade: warm fiery orange, so it stands out and invites a tap
        makeHot(instantLink)
        look.skin?.let { applySkin(it) }
    }

    /** Close All, with the mode underneath so it's clear which clean it does. */
    private fun sweepLabel(): CharSequence {
        val rapid = Shell.isReady()
        val sb = android.text.SpannableStringBuilder(
            if (rapid) "RAPID TOTAL CLEAN" else "[ BASIC CLEAN & CACHE CLEAR ]"
        )
        val st = sb.length
        // in Rapid they're already in the zone: no need to sell it to them, just the name
        if (rapid) return sb
        sb.append("\nIndividual app closer (average user)")
        sb.setSpan(android.text.style.RelativeSizeSpan(0.62f), st, sb.length, 0)
        sb.setSpan(android.text.style.ForegroundColorSpan(if (rapid) 0xFFFFB23A.toInt() else Hud.CYAN), st, sb.length, 0)
        return sb
    }

    /** The Rapid Total Clean button: name on top (with the fire), a short reason underneath. */
    private fun rapidLabel(reconnect: Boolean): CharSequence {
        val sb = android.text.SpannableStringBuilder("RAPID TOTAL CLEAN")
        val st = sb.length
        sb.append(if (reconnect) "\nTap to reconnect" else "\nInstant complete clean (confident user)")
        sb.setSpan(android.text.style.RelativeSizeSpan(0.62f), st, sb.length, 0)
        sb.setSpan(android.text.style.ForegroundColorSpan(0xFFFFC08A.toInt()), st, sb.length, 0)
        return sb
    }

    private var skinHeader: SkinHeaderView? = null

    /** The Close All button's look: themed picture, or the HUD blue (green while it cleans). */
    private fun setSweepLook(cleaning: Boolean) {
        val skin = Themes.look.skin
        sweepButton.background = if (skin != null) {
            android.graphics.drawable.BitmapDrawable(resources, android.graphics.BitmapFactory.decodeResource(
                resources, if (cleaning) skin.rapid else skin.basic, android.graphics.BitmapFactory.Options().apply { inScaled = false }))
        } else if (!cleaning && Shell.isReady()) {
            makeHot(sweepButton)
            return
        } else {
            Hud.button(this, if (cleaning) Hud.GREEN else Hud.BLUE)
        }
        // back to the normal look
        val d = resources.displayMetrics.density
        sweepButton.setPadding((12 * d).toInt(), sweepButton.paddingTop, (12 * d).toInt(), sweepButton.paddingBottom)
        sweepButton.setShadowLayer(10f, 0f, 0f, 0xAA35D7FF.toInt())
    }

    /** The hot Rapid look: orange glow, flames off the corners, a speedometer at each end. */
    private fun makeHot(b: android.widget.Button) {
        val d = resources.displayMetrics.density
        b.background = HotButtonDrawable(d)
        val side = (HotButtonDrawable.SIDE_DP * d).toInt()
        b.setPadding(side, (14 * d).toInt(), side, (14 * d).toInt())
        b.setShadowLayer(12f, 0f, 0f, 0xFFFF7A18.toInt())
        (b as? SpeedButton)?.fire = false
    }

    /** Illustrated theme: put the theme's pictures behind every part of the screen. */
    private fun applySkin(skin: Skin) {
        val d = resources.displayMetrics.density
        fun pic(res: Int) = android.graphics.drawable.BitmapDrawable(resources,
            android.graphics.BitmapFactory.decodeResource(resources, res, android.graphics.BitmapFactory.Options().apply { inScaled = false }))
        findViewById<View>(R.id.mainScroll)?.setBackgroundResource(skin.background)
        // top picture with the live RAM figure in its dial, instead of the two rams
        (findViewById<View>(R.id.ramPanel) as? LinearLayout)?.let { panel ->
            panel.removeAllViews()
            panel.background = null
            panel.setPadding(0, 0, 0, 0)
            val v = SkinHeaderView(this, skin)
            panel.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            skinHeader = v
        }
        sweepButton.background = pic(skin.basic)
        sweepButton.setShadowLayer(6f, 0f, 2f, 0xFF000000.toInt())
        // keep the words clear of the pictures at each end of the buttons
        sweepButton.textSize = 18f
        sweepButton.setPadding((58 * d).toInt(), sweepButton.paddingTop, (58 * d).toInt(), sweepButton.paddingBottom)
        instantLink.textSize = 20f
        instantLink.setPadding((62 * d).toInt(), instantLink.paddingTop, (62 * d).toInt(), instantLink.paddingBottom)
        ramFreeText.textSize = 13f
        appsCountText.textSize = 13f
        instantLink.background = pic(skin.rapid)
        (instantLink as? SpeedButton)?.fire = false
        instantLink.setShadowLayer(6f, 0f, 2f, 0xFF000000.toInt())
        findViewById<View>(R.id.listPanel)?.background = FrameDrawable(this, skin.list)
        ramFreeText.background = pic(skin.tagFree)
        ramFreeText.setTextColor(skin.tagFreeText)
        ramFreeText.setPadding((44 * d).toInt(), (6 * d).toInt(), (18 * d).toInt(), (6 * d).toInt())
        appsCountText.background = pic(skin.tagApps)
        appsCountText.setTextColor(skin.tagAppsText)
        appsCountText.setPadding((40 * d).toInt(), (6 * d).toInt(), (16 * d).toInt(), (6 * d).toInt())
        closeX?.apply {
            background = pic(skin.close)
            text = ""
        }
    }

    /** Pick a screen theme. Cyber Cyan is free; the rest come with Pro. */
    private fun showThemes() {
        val d = resources.displayMetrics.density
        val pro = Unlock.isUnlocked(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (18 * d).toInt(), (20 * d).toInt(), (10 * d).toInt())
        }
        box.addView(TextView(this).apply {
            text = "SCREEN THEMES"
            textSize = 20f
            setTextColor(0xFFE6F7FF.toInt())
            setPadding(0, 0, 0, (6 * d).toInt())
        })
        if (!pro) box.addView(TextView(this).apply {
            text = "Cyber Cyan is free. The other themes come with App Closer Pro."
            textSize = 14f
            setTextColor(0xFF7FA6BF.toInt())
            setPadding(0, 0, 0, (8 * d).toInt())
        })
        val dialog = AlertDialog.Builder(this).setView(android.widget.ScrollView(this).apply { addView(box) }).create()
        for (t in Themes.all) {
            val locked = !pro && !Themes.isFree(t)
            box.addView(Button(this).apply {
                text = (if (t == Themes.look) "✓  " else "") + t.name + (if (locked) "   🔒 PRO" else "")
                isAllCaps = false
                textSize = 17f
                setTextColor(t.edge)
                stateListAnimator = null
                background = HudDrawable(d, t.edge, 0xFF0B1A28.toInt(), 0xFF081320.toInt(), cut = 12f, accents = false)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (60 * d).toInt())
                    .apply { topMargin = (6 * d).toInt() }
                setOnClickListener {
                    dialog.dismiss()
                    if (locked) {
                        showUnlock()
                    } else if (t != Themes.look) {
                        Themes.choose(this@MainActivity, t)
                        recreate()      // redraw everything in the new colours
                    }
                }
            })
        }
        dialog.window?.setBackgroundDrawable(Hud.panel(this))
        dialog.show()
    }

    /** "[ 05 ]" with the brackets in the box's colour. */
    private fun bracketed(value: String, color: Int): CharSequence = colored("[ ", color, value, 0, " ]", color)

    /** Rapid Total Clean finished: the smiling ram with glowing horns, ALL CLEAN and the time taken. */
    private fun showRapidAllClean(seconds: Float) {
        if (isFinishing) return
        val d = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        val frame = android.widget.FrameLayout(this).apply {
            setBackgroundColor(0xE607070F.toInt())
            val v = RapidCleanView(this@MainActivity).also { it.seconds = seconds }
            val wpx = (minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) * 0.9f).toInt()
            addView(v, android.widget.FrameLayout.LayoutParams(wpx, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER))
            setOnClickListener { d.dismiss() }
        }
        d.setContentView(frame)
        d.show()
        frame.postDelayed({ if (d.isShowing) d.dismiss() }, 4500)
    }

    private fun savePendingRapid(r: SweepResult, secs: Float) {
        prefs.edit()
            .putBoolean("rapid_pending", true)
            .putInt("rapid_closed", r.closed)
            .putLong("rapid_ram", r.ramFreed)
            .putLong("rapid_cache", r.cacheFreed)
            .putFloat("rapid_secs", secs)
            .putLong("rapid_at", System.currentTimeMillis())
            .apply()
    }

    /** Shows a finished Rapid clean's results (once), e.g. after Recents wiping reopened the app. */
    private fun consumePendingRapid() {
        if (!prefs.getBoolean("rapid_pending", false)) return
        val fresh = System.currentTimeMillis() - prefs.getLong("rapid_at", 0) < 60_000
        val r = SweepResult(prefs.getInt("rapid_closed", 0), prefs.getLong("rapid_ram", 0), prefs.getLong("rapid_cache", 0))
        val secs = prefs.getFloat("rapid_secs", 0f)
        prefs.edit().putBoolean("rapid_pending", false).apply()
        if (!fresh || isFinishing) return
        cleanGauge.finish()
        showSweepDone(r)
        showRapidAllClean(secs)
    }

    private fun setOwnRecentsHidden(hidden: Boolean) {
        try {
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).appTasks
                .forEach { it.setExcludeFromRecents(hidden) }
        } catch (_: Exception) {
        }
    }

    /** Counts one free Rapid try and says how many are left. */
    private fun countFreeRapid() {
        if (Unlock.isUnlocked(this)) return
        Unlock.useRapid(this)
        val left = Unlock.freeRapidLeft(this)
        toast(if (left == 0) "That was your last free Rapid clean" else "Free Rapid clean ${Unlock.rapidUsed(this)} of ${Unlock.FREE_RAPID}")
    }

    /** Counts one free Close All clean and says how many are left. */
    private fun countFreeClean() {
        if (Unlock.isUnlocked(this)) return
        Unlock.useClean(this)
        val left = Unlock.freeCleansLeft(this)
        toast(if (left == 0) "That was your last free clean" else "Free clean ${Unlock.cleansUsed(this)} of ${Unlock.FREE_CLEANS}")
    }

    /** The "Unlock App Closer" screen: their own results, what's included, one button. */
    private fun showUnlock() {
        if (unlockDialog?.isShowing == true) return
        val view = layoutInflater.inflate(R.layout.dialog_unlock, null)
        val stats = view.findViewById<TextView>(R.id.unlockStats)
        val buy = view.findViewById<Button>(R.id.unlockBuy)
        val closed = Unlock.totalClosed(this)
        val cache = Unlock.totalCache(this)
        stats.text = if (closed > 0 || cache > 0) {
            colored("You've closed ", 0, "$closed apps", RED, " and cleared ", 0,
                AppAdapter.formatBytes(cache), GREEN, " of cache with App Closer.")
        } else {
            if (Unlock.freeRapidLeft(this) == 0) "You've used your ${Unlock.FREE_RAPID} free Rapid cleans."
            else "You've used your ${Unlock.FREE_CLEANS} free Basic cleans."
        }
        fun setPrice() {
            buy.text = Unlock.price()?.let { "GET PRO FOR $it · ONE-OFF" } ?: "GET PRO · ONE-OFF"
        }
        setPrice()
        Unlock.connect(this) { if (unlockDialog?.isShowing == true) setPrice() }
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        buy.setOnClickListener { Unlock.buy(this)?.let { toast(it) } }
        view.findViewById<Button>(R.id.unlockLater).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.unlockRestore).setOnClickListener {
            toast("Checking Google Play…")
            Unlock.connect(this) {
                if (!Unlock.isUnlocked(this)) toast("No purchase found on this Google account")
            }
        }
        unlockDialog = dialog
        dialog.show()
        buy.requestFocus()
    }

    private fun showOptions(app: RunningApp) {
        val actions = mutableListOf<Action>()
        if (!app.isProtected && app.isRunning) actions.add(Action("Close it now") { closeApp(app) })
        if ((easyMode || Sweeper.canClearSingleCache) && app.cacheBytes != 0L) {
            val size = if (app.cacheBytes > 0) " (${AppAdapter.formatBytes(app.cacheBytes)})" else ""
            actions.add(Action("Clear its cache$size") { clearCache(app) })
        }
        if (Sweeper.canBlockBackground && !easyMode && !tvGuided) {
            if (app.backgroundBlocked) {
                actions.add(Action("Let it auto-start again") { setBlocked(app, false) })
            } else {
                actions.add(Action("Stop it auto-starting") { setBlocked(app, true) })
            }
        }
        if (app.isProtected) {
            actions.add(Action("Remove protection") { setProtected(app, false) })
        } else if (!app.isSystem) {
            actions.add(Action("Protect it (Close All will skip it)") { setProtected(app, true) })
        }
        actions.add(Action("Open Android's App info page") { openAppInfo(app) })

        val title = if (app.isSystem) "${app.label}  ·  system app, close with care" else app.label
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(actions.map { it.label }.toTypedArray()) { _, which -> actions[which].run() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun quickClose(app: RunningApp) {
        if (!app.isRunning) return
        if (app.isProtected) {
            toast("${app.label} is protected. Tap it to change that.")
            return
        }
        closeApp(app)
    }

    private fun closeApp(app: RunningApp) {
        if (tvGuided) {
            startGuided(listOf(app))
            return
        }
        if (easyMode) {
            val svc = SweepAccessibilityService.instance ?: return
            startSweepUi()
            if (!svc.sweep(listOf(app.packageName to app.label), forceStop = true, clearCache = false)) endSweepUi()
            return
        }
        lifecycleScope.launch {
            val before = sweeper.availableRam()
            withContext(Dispatchers.IO) {
                sweeper.forceStop(app.packageName)
                Thread.sleep(600)
            }
            val freed = (sweeper.availableRam() - before).coerceAtLeast(0)
            toast("Closed ${app.label} · freed ${AppAdapter.formatBytes(freed)} RAM")
            refresh()
        }
    }

    private fun clearCache(app: RunningApp) {
        if (tvGuided) {
            openAppInfo(app)
            return
        }
        if (easyMode) {
            val svc = SweepAccessibilityService.instance ?: return
            startSweepUi()
            if (!svc.sweep(listOf(app.packageName to app.label), forceStop = false, clearCache = true)) endSweepUi()
            return
        }
        lifecycleScope.launch {
            val freed = withContext(Dispatchers.IO) { sweeper.clearAppCache(app.packageName) }
            toast("Cleared ${AppAdapter.formatBytes(freed)} of cache from ${app.label}")
            refresh()
        }
    }

    private fun setBlocked(app: RunningApp, block: Boolean) {
        if (block && !Unlock.canRapid(this)) {
            showUnlock()
            return
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { sweeper.setAutoStartBlocked(app.packageName, block) }
            toast(
                if (block) "${app.label} won't start by itself any more. It still opens when you tap it."
                else "${app.label} can start by itself again."
            )
            refresh()
        }
    }

    private fun setProtected(app: RunningApp, protect: Boolean) {
        sweeper.setProtected(app.packageName, protect)
        toast(if (protect) "${app.label} won't be closed by Close All" else "${app.label} is no longer protected")
        showList(currentApps.map {
            if (it.packageName == app.packageName) it.copy(isProtected = protect) else it
        })
    }

    // ---------- Helpers ----------

    private fun updateMemory() {
        val info = sweeper.memoryInfo()
        if (info.totalMem <= 0) return
        val used = info.totalMem - info.availMem
        ramGauge.setPercent(used * 100f / info.totalMem)
        skinHeader?.percent = used * 100f / info.totalMem
        // Used = red, available = green, everything else white
        ramUsedText.text = colored(gb(used), Hud.RED, " / ", 0xFF5C7F99.toInt(), gb(info.totalMem), Hud.GREEN)
        ramFreeText.text = "${gb(info.availMem)} AVAILABLE"
    }

    /** 4.25 GB style */
    private fun gb(bytes: Long): String {
        val v = bytes / 1024.0 / 1024.0 / 1024.0
        return String.format("%.2f", v).trimEnd('0').trimEnd('.') + " GB"
    }

    private fun openAppInfo(app: RunningApp) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
        } catch (_: ActivityNotFoundException) {
            toast("This device has no App info page")
        }
    }

    private fun openShizuku() {
        val intent = packageManager.getLaunchIntentForPackage(Shell.SHIZUKU_PACKAGE)
            ?: packageManager.getLeanbackLaunchIntentForPackage(Shell.SHIZUKU_PACKAGE)
        if (intent != null) startActivity(intent) else toast("Couldn't open Shizuku")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

private const val RED = 0xFFE53935.toInt()
private const val GREEN = 0xFF00E676.toInt()

/** Builds text where some parts are coloured: colored("4.97 GB", RED, " / 7.26 GB"). Colour 0 = white. */
private fun colored(vararg parts: Any): CharSequence {
    val sb = android.text.SpannableStringBuilder()
    var i = 0
    while (i < parts.size) {
        val text = parts[i].toString()
        val color = if (i + 1 < parts.size && parts[i + 1] is Int) parts[i + 1] as Int else 0
        val start = sb.length
        sb.append(text)
        if (color != 0) {
            sb.setSpan(android.text.style.ForegroundColorSpan(color), start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        i += if (i + 1 < parts.size && parts[i + 1] is Int) 2 else 1
    }
    return sb
}

private class Action(val label: String, val run: () -> Unit)

fun resultLine(r: SweepResult): String =
    "Closed ${r.closed} app${if (r.closed == 1) "" else "s"} · freed ${AppAdapter.formatBytes(r.ramFreed)} RAM · " +
        "cleared ${AppAdapter.formatBytes(r.cacheFreed)} of cache"
