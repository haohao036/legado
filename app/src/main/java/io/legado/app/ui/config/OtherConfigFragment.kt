package io.legado.app.ui.config

import android.app.Activity.RESULT_OK
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.documentfile.provider.DocumentFile
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.legado.app.App
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.AppConfig
import io.legado.app.help.BookHelp
import io.legado.app.help.permission.Permissions
import io.legado.app.help.permission.PermissionsCompat
import io.legado.app.lib.theme.ATH
import io.legado.app.receiver.SharedReceiverActivity
import io.legado.app.service.WebService
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.*
import java.io.File


class OtherConfigFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val requestCodeCover = 231

    private val packageManager = App.INSTANCE.packageManager
    private val componentName = ComponentName(
        App.INSTANCE,
        SharedReceiverActivity::class.java.name
    )
    private val webPort get() = getPrefInt(PreferKey.webPort, 1122)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        putPrefBoolean(PreferKey.processText, isProcessTextEnabled())
        addPreferencesFromResource(R.xml.pref_config_other)
        upPreferenceSummary(PreferKey.threadCount, AppConfig.threadCount.toString())
        upPreferenceSummary(PreferKey.webPort, webPort.toString())
        upPreferenceSummary(PreferKey.defaultCover, getPrefString(PreferKey.defaultCover))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        ATH.applyEdgeEffectColor(listView)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        when (preference?.key) {
            PreferKey.threadCount -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.threads_num_title))
                .setMaxValue(999)
                .setMinValue(1)
                .setValue(AppConfig.threadCount)
                .show {
                    AppConfig.threadCount = it
                }
            PreferKey.webPort -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.web_port_title))
                .setMaxValue(60000)
                .setMinValue(1024)
                .setValue(webPort)
                .show {
                    putPrefInt(PreferKey.webPort, it)
                }
            PreferKey.cleanCache -> {
                BookHelp.clearCache()
                requireActivity().cacheDir.listFiles()?.forEach {
                    it.delete()
                }
                toast(R.string.clear_cache_success)
            }
            PreferKey.defaultCover -> {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "image/*"
                startActivityForResult(intent, requestCodeCover)
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PreferKey.threadCount -> upPreferenceSummary(
                key, AppConfig.threadCount.toString()
            )
            PreferKey.webPort -> {
                upPreferenceSummary(key, webPort.toString())
                if (WebService.isRun) {
                    WebService.stop(requireContext())
                    WebService.start(requireContext())
                }
            }
            PreferKey.recordLog -> LogUtils.upLevel()
            PreferKey.processText -> sharedPreferences?.let {
                setProcessTextEnable(it.getBoolean(key, true))
            }
            PreferKey.showRss -> postEvent(EventBus.SHOW_RSS, "unused")
            PreferKey.defaultCover -> upPreferenceSummary(
                key,
                getPrefString(PreferKey.defaultCover)
            )
        }
    }

    private fun upPreferenceSummary(preferenceKey: String, value: String?) {
        val preference = findPreference<Preference>(preferenceKey) ?: return
        when (preferenceKey) {
            PreferKey.threadCount -> preference.summary = getString(R.string.threads_num, value)
            PreferKey.webPort -> preference.summary = getString(R.string.web_port_summary, value)
            else -> if (preference is ListPreference) {
                val index = preference.findIndexOfValue(value)
                // Set the summary to reflect the new value.
                preference.summary = if (index >= 0) preference.entries[index] else null
            } else {
                preference.summary = value
            }
        }
    }

    private fun isProcessTextEnabled(): Boolean {
        return packageManager.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun setProcessTextEnable(enable: Boolean) {
        if (enable) {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
        } else {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun setCoverFromUri(uri: Uri) {
        if (uri.toString().isContentPath()) {
            val doc = DocumentFile.fromSingleUri(requireContext(), uri)
            doc?.name?.let {
                var file = requireContext().externalFilesDir
                file = FileUtils.createFileIfNotExist(file, it, "covers")
                kotlin.runCatching {
                    DocumentUtils.readBytes(requireContext(), doc.uri)
                }.getOrNull()?.let { byteArray ->
                    file.writeBytes(byteArray)
                    putPrefString(PreferKey.defaultCover, file.absolutePath)
                    CoverImageView.upDefaultCover()
                } ?: toast("获取文件出错")
            }
        } else {
            PermissionsCompat.Builder(this)
                .addPermissions(
                    Permissions.READ_EXTERNAL_STORAGE,
                    Permissions.WRITE_EXTERNAL_STORAGE
                )
                .rationale(R.string.bg_image_per)
                .onGranted {
                    RealPathUtil.getPath(requireContext(), uri)?.let { path ->
                        val imgFile = File(path)
                        if (imgFile.exists()) {
                            var file = requireContext().externalFilesDir
                            file = FileUtils.createFileIfNotExist(file, imgFile.name, "covers")
                            file.writeBytes(imgFile.readBytes())
                            putPrefString(PreferKey.defaultCover, file.absolutePath)
                            CoverImageView.upDefaultCover()
                        }
                    }
                }
                .request()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            requestCodeCover -> if (resultCode == RESULT_OK) {
                data?.data?.let { uri ->
                    setCoverFromUri(uri)
                }
            }
        }
    }

}