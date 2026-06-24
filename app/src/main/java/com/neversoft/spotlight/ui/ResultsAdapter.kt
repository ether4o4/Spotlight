package com.neversoft.spotlight.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.neversoft.spotlight.R
import com.neversoft.spotlight.databinding.ItemResultBinding
import com.neversoft.spotlight.model.ResultType
import com.neversoft.spotlight.model.SearchResult
import java.util.Locale

class ResultsAdapter(
    private val onClick: (SearchResult) -> Unit,
) : ListAdapter<SearchResult, ResultsAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemResultBinding,
        private val onClick: (SearchResult) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchResult) {
            val context = binding.root.context
            binding.resultTitle.text = item.title
            binding.resultSubtitle.text = buildSubtitle(item)
            binding.resultType.text = context.getString(item.type.labelRes)

            bindIcon(context, item)

            binding.root.setOnClickListener { onClick(item) }
        }

        private fun bindIcon(context: Context, item: SearchResult) {
            // Show a real launcher icon for apps; otherwise a tinted type glyph.
            if (item.type == ResultType.APP && item.appPackage != null) {
                val drawable = runCatching {
                    context.packageManager.getApplicationIcon(item.appPackage)
                }.getOrNull()
                if (drawable != null) {
                    binding.resultIcon.setImageDrawable(drawable)
                    binding.resultIcon.imageTintList = null
                    return
                }
            }
            binding.resultIcon.setImageResource(item.type.iconRes)
            val tint = when (item.type) {
                ResultType.IMAGE, ResultType.VIDEO, ResultType.AUDIO ->
                    ContextCompat.getColor(context, R.color.neversoft_red_bright)
                else -> ContextCompat.getColor(context, R.color.text_secondary)
            }
            binding.resultIcon.setColorFilter(tint)
        }

        private fun buildSubtitle(item: SearchResult): String {
            val size = if (item.sizeBytes > 0) formatSize(item.sizeBytes) else null
            return listOfNotNull(item.subtitle.takeIf { it.isNotBlank() }, size)
                .joinToString("  ·  ")
        }

        private fun formatSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val units = arrayOf("KB", "MB", "GB", "TB")
            var value = bytes.toDouble() / 1024
            var i = 0
            while (value >= 1024 && i < units.size - 1) {
                value /= 1024
                i++
            }
            return String.format(Locale.US, "%.1f %s", value, units[i])
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SearchResult>() {
            override fun areItemsTheSame(a: SearchResult, b: SearchResult) = a.id == b.id
            override fun areContentsTheSame(a: SearchResult, b: SearchResult) = a == b
        }
    }
}
