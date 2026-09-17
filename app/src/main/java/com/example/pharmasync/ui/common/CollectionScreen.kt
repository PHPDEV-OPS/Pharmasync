package com.example.pharmasync.ui.common

import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.widget.doAfterTextChanged
import com.example.pharmasync.databinding.FragmentCollectionBinding
import com.example.pharmasync.util.visibleIf
import com.google.android.material.chip.Chip

/** Configures the shared list screen layout ([FragmentCollectionBinding]). */
class CollectionScreen(val binding: FragmentCollectionBinding) {
    private val context = binding.root.context

    fun header(title: CharSequence, subtitle: CharSequence? = null) {
        binding.header.headerTitle.text = title
        binding.header.headerSubtitle.text = subtitle
        binding.header.headerSubtitle.visibleIf(!subtitle.isNullOrBlank())
    }

    fun headerAction(@DrawableRes icon: Int, description: CharSequence, onClick: () -> Unit) = with(binding.header.headerAction) {
        setIconResource(icon)
        contentDescription = description
        tooltipText = description
        visibility = View.VISIBLE
        setOnClickListener { onClick() }
    }

    fun backButton(onClick: () -> Unit) = with(binding.header.headerBack) {
        visibility = View.VISIBLE
        setOnClickListener { onClick() }
    }

    fun search(hint: CharSequence, onQuery: (String) -> Unit) {
        binding.searchLayout.visibility = View.VISIBLE
        binding.searchInput.hint = hint
        binding.searchInput.doAfterTextChanged { onQuery(it?.toString()?.trim().orEmpty()) }
    }

    fun hideSearch() {
        binding.searchLayout.visibility = View.GONE
    }

    fun filters(labels: List<CharSequence>, selected: Int = 0, onSelected: (Int) -> Unit) {
        binding.filterScroll.visibility = View.VISIBLE
        binding.filterChips.removeAllViews()
        labels.forEachIndexed { index, label ->
            val chip = Chip(context, null, com.google.android.material.R.attr.chipStyle).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isCheckedIconVisible = false
                isChecked = index == selected
                setOnClickListener { onSelected(index) }
            }
            binding.filterChips.addView(chip)
        }
    }

    fun fab(text: CharSequence, @DrawableRes icon: Int, onClick: () -> Unit) = with(binding.fab) {
        this.text = text
        setIconResource(icon)
        visibility = View.VISIBLE
        setOnClickListener { onClick() }
        // Shrink while scrolling down so the FAB doesn't hide the last card.
        binding.recycler.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy > 8 && isExtended) shrink() else if (dy < -8 && !isExtended) extend()
            }
        })
    }

    fun loading(isLoading: Boolean) {
        binding.progress.visibility = if (isLoading) View.VISIBLE else View.INVISIBLE
    }

    fun empty(
        visible: Boolean,
        @DrawableRes icon: Int = 0,
        title: CharSequence = "",
        message: CharSequence = "",
        actionText: CharSequence? = null,
        action: (() -> Unit)? = null,
    ) {
        binding.emptyContainer.visibleIf(visible)
        binding.swipeRefresh.visibleIf(!visible)
        if (!visible) return
        with(binding.emptyState) {
            if (icon != 0) emptyIcon.setImageResource(icon)
            emptyTitle.text = title
            emptyMessage.text = message
            emptyAction.visibleIf(actionText != null && action != null)
            emptyAction.text = actionText
            emptyAction.setOnClickListener { action?.invoke() }
        }
    }
}
