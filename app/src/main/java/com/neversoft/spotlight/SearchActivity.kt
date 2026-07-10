package com.neversoft.spotlight

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.neversoft.spotlight.databinding.ActivitySearchBinding
import com.neversoft.spotlight.model.FilterSelection
import com.neversoft.spotlight.model.LaunchAction
import com.neversoft.spotlight.model.PrimaryFilter
import com.neversoft.spotlight.model.StorageScope
import com.neversoft.spotlight.model.SubFilter
import com.neversoft.spotlight.search.SearchEngine
import com.neversoft.spotlight.ui.ResultsAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var engine: SearchEngine
    private val adapter = ResultsAdapter(::launchResult)

    private var selection = FilterSelection()
    private var scope = StorageScope.ALL
    private var searchJob: Job? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshAccessState()
            runSearch()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        engine = SearchEngine(applicationContext)

        binding.resultsList.layoutManager = LinearLayoutManager(this)
        binding.resultsList.adapter = adapter

        setupSearchInput()
        setupPrimaryChips()
        setupScopeChips()
        binding.backButton.setOnClickListener { finish() }
        binding.grantButton.setOnClickListener { openAllFilesSettings() }

        applyIntent(intent)
        requestRuntimePermissionsIfNeeded()
        // Populate immediately in "browse" mode; the permission callback re-runs this
        // once media/contacts access is granted so those results fill in too.
        runSearch()

        showBootSplash(savedInstanceState)
    }

    /**
     * Split-second "NeverSoft Services" boot splash. Only on a fresh launch (not on
     * config-change/restore), it fades out after a brief delay and hands off to the
     * real search UI. It never blocks input beyond its own lifetime.
     */
    private fun showBootSplash(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            binding.splashOverlay.visibility = android.view.View.GONE
            return
        }
        binding.splashOverlay.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            delay(SPLASH_MS)
            binding.splashOverlay.animate()
                .alpha(0f)
                .setDuration(280L)
                .withEndAction {
                    binding.splashOverlay.visibility = android.view.View.GONE
                    binding.splashOverlay.alpha = 1f
                }
                .start()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshAccessState()
    }

    // --- Intent handling (widget entry points) ------------------------------

    private fun applyIntent(intent: Intent) {
        val primaryName = intent.getStringExtra(EXTRA_PRIMARY)
        if (primaryName != null) {
            val primary = runCatching { PrimaryFilter.valueOf(primaryName) }.getOrNull()
            if (primary != null) selectPrimaryChip(primary)
        }
        if (intent.getBooleanExtra(EXTRA_FOCUS, false)) {
            binding.searchInput.requestFocus()
            binding.searchInput.post { showKeyboard() }
        }
    }

    // --- Search input -------------------------------------------------------

    private fun setupSearchInput() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.clearButton.visibility =
                    if (s.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                scheduleSearch()
            }
        })

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                runSearch()
                true
            } else {
                false
            }
        }

        binding.clearButton.setOnClickListener {
            binding.searchInput.setText("")
            binding.searchInput.requestFocus()
            showKeyboard()
        }
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(DEBOUNCE_MS)
            runSearch()
        }
    }

    private fun runSearch() {
        searchJob?.cancel()
        val query = binding.searchInput.text?.toString().orEmpty().trim()
        binding.statusText.text = getString(R.string.searching)
        searchJob = lifecycleScope.launch {
            val results = engine.search(query, selection, scope)
            adapter.submitList(results)
            if (results.isEmpty()) {
                val message = if (query.isEmpty()) {
                    getString(R.string.empty_start)
                } else {
                    getString(R.string.empty_no_results, query)
                }
                showEmptyState(message)
                binding.statusText.text = ""
            } else {
                binding.emptyState.visibility = android.view.View.GONE
                binding.statusText.text = getString(R.string.results_count, results.size)
            }
        }
    }

    private fun showEmptyState(text: String) {
        binding.emptyState.text = text
        binding.emptyState.visibility = android.view.View.VISIBLE
    }

    // --- Filter chips -------------------------------------------------------

    private fun setupPrimaryChips() {
        binding.primaryChips.removeAllViews()
        for (filter in PrimaryFilter.entries) {
            val chip = buildChip(getString(filter.labelRes), filter.iconRes).apply {
                tag = filter
                isChecked = filter == selection.primary
            }
            binding.primaryChips.addView(chip)
        }
        binding.primaryChips.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) }
            val primary = chip?.tag as? PrimaryFilter ?: return@setOnCheckedStateChangeListener
            selection = selection.withPrimary(primary)
            buildSecondaryChips(primary)
            runSearch()
        }
        buildSecondaryChips(selection.primary)
    }

    private fun buildSecondaryChips(primary: PrimaryFilter) {
        val subs = primary.subFilters()
        binding.secondaryChips.removeAllViews()
        if (subs.isEmpty()) {
            binding.secondaryScroll.visibility = android.view.View.GONE
            return
        }
        binding.secondaryScroll.visibility = android.view.View.VISIBLE
        for (sub in subs) {
            val chip = buildChip(getString(sub.labelRes), null).apply {
                tag = sub
                isChecked = sub == selection.sub
            }
            binding.secondaryChips.addView(chip)
        }
        binding.secondaryChips.setOnCheckedStateChangeListener { group, checkedIds ->
            val sub = checkedIds.firstOrNull()
                ?.let { group.findViewById<Chip>(it) }
                ?.tag as? SubFilter
            selection = selection.copy(sub = sub)
            runSearch()
        }
    }

    private fun selectPrimaryChip(primary: PrimaryFilter) {
        for (i in 0 until binding.primaryChips.childCount) {
            val chip = binding.primaryChips.getChildAt(i) as? Chip ?: continue
            if (chip.tag == primary) {
                chip.isChecked = true
                break
            }
        }
    }

    private fun setupScopeChips() {
        binding.scopeChips.removeAllViews()
        for (s in StorageScope.entries) {
            val chip = buildChip(getString(s.labelRes), null).apply {
                tag = s
                isChecked = s == scope
            }
            binding.scopeChips.addView(chip)
        }
        binding.scopeChips.setOnCheckedStateChangeListener { group, checkedIds ->
            val selected = checkedIds.firstOrNull()
                ?.let { group.findViewById<Chip>(it) }
                ?.tag as? StorageScope ?: return@setOnCheckedStateChangeListener
            scope = selected
            runSearch()
        }
    }

    private fun buildChip(text: String, iconRes: Int?): Chip {
        val chip = Chip(this)
        chip.text = text
        chip.isCheckable = true
        chip.isClickable = true
        chip.isCheckedIconVisible = false
        chip.setEnsureMinTouchTargetSize(false)
        chip.chipBackgroundColor = ContextCompat.getColorStateList(this, R.color.chip_bg_color)
        chip.setTextColor(ContextCompat.getColorStateList(this, R.color.chip_text_color))
        chip.chipStrokeColor = ContextCompat.getColorStateList(this, R.color.chip_stroke_color)
        chip.chipStrokeWidth = resources.displayMetrics.density * 1f
        if (iconRes != null) {
            chip.setChipIconResource(iconRes)
            chip.chipIconTint = ContextCompat.getColorStateList(this, R.color.chip_text_color)
            chip.isChipIconVisible = true
            chip.iconStartPadding = resources.displayMetrics.density * 2f
        }
        return chip
    }

    // --- Permissions / access ----------------------------------------------

    private fun requestRuntimePermissionsIfNeeded() {
        val needed = requiredRuntimePermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun requiredRuntimePermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        add(Manifest.permission.READ_CONTACTS)
    }

    private fun refreshAccessState() {
        // Banner nudges the user toward full-device (all-files) access, which is
        // what makes "search the entire phone" actually cover every folder.
        val hasFull = engine.hasFullFileAccess()
        binding.permissionBanner.visibility =
            if (hasFull) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun openAllFilesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appSpecific = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            if (!launchOrNull(appSpecific)) {
                launchOrNull(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            // Pre-R: the legacy storage permission is the runtime grant.
            requestRuntimePermissionsIfNeeded()
        }
    }

    // --- Launching results --------------------------------------------------

    private fun launchResult(result: com.neversoft.spotlight.model.SearchResult) {
        when (val action = result.launch) {
            is LaunchAction.LaunchApp -> {
                val intent = packageManager.getLaunchIntentForPackage(action.packageName)
                if (intent != null) startActivity(intent) else toast(R.string.no_app_to_open)
            }

            is LaunchAction.OpenUri ->
                viewIntent(Uri.parse(action.uri), action.mimeType)

            is LaunchAction.OpenFile ->
                openFile(action.path, action.mimeType)

            is LaunchAction.ViewContact ->
                viewIntent(Uri.parse(action.lookupUri), null)
        }
    }

    private fun openFile(path: String, mimeType: String) {
        val file = File(path)
        if (mimeType == "resource/folder" || file.isDirectory) {
            openFolder(path)
            return
        }
        val uri = runCatching {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }.getOrNull()
        if (uri == null) {
            toast(R.string.no_app_to_open)
            return
        }
        viewIntent(uri, mimeType)
    }

    /**
     * Open a folder result in the system file manager. Maps the filesystem path to
     * an ExternalStorageProvider "documents" URI (only the primary volume maps
     * deterministically), then falls back to browsing storage, then a toast.
     *
     * Security: [path] comes from our own on-device file walk, not untrusted input,
     * so there is no traversal/injection surface; we hand off via ACTION_VIEW and
     * never expose our own FileProvider here.
     */
    private fun openFolder(path: String) {
        val authority = "com.android.externalstorage.documents"
        val primaryRoot = Environment.getExternalStorageDirectory()?.absolutePath

        if (primaryRoot != null && path.startsWith(primaryRoot)) {
            val relative = path.removePrefix(primaryRoot).trim('/')
            val documentUri = runCatching {
                DocumentsContract.buildDocumentUri(authority, "primary:$relative")
            }.getOrNull()
            if (documentUri != null) {
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (launchOrNull(view)) return
            }
        }

        // Fall back to opening the storage root in a documents browser.
        val browseRoot = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                DocumentsContract.buildRootUri(authority, "primary"),
                "vnd.android.document/root",
            )
        }
        if (launchOrNull(browseRoot)) return

        toast(getString(R.string.type_folder) + ": " + path)
    }

    private fun viewIntent(uri: Uri, mimeType: String?) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (mimeType != null) setDataAndType(uri, mimeType) else data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (!launchOrNull(intent)) toast(R.string.no_app_to_open)
    }

    private fun launchOrNull(intent: Intent): Boolean = runCatching {
        startActivity(intent)
        true
    }.getOrDefault(false)

    // --- Keyboard helpers ---------------------------------------------------

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_PRIMARY = "com.neversoft.spotlight.extra.PRIMARY"
        const val EXTRA_FOCUS = "com.neversoft.spotlight.extra.FOCUS"
        private const val DEBOUNCE_MS = 220L
        private const val SPLASH_MS = 900L
    }
}
