package com.blueberry.client.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blueberry.client.R
import com.blueberry.client.core.IModule
import com.blueberry.client.core.ModuleBootstrap
import com.blueberry.client.core.ModuleCategory
import com.blueberry.client.core.ModuleContext
import com.blueberry.client.core.ModuleRegistry
import com.blueberry.client.core.OverlayController

/**
 * Toggle UI for all registered modules, grouped by category with search + collapse.
 */
class ClickGuiActivity : AppCompatActivity() {

    private lateinit var adapter: ModuleListAdapter
    private val collapsed = mutableSetOf<ModuleCategory>()
    private var filterQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clickgui)

        // Ensure modules exist even if OverlayService hasn't started yet
        ModuleBootstrap.ensureRegistered(
            ModuleContext(OverlayController(this), LauncherActivity.networkClient)
        )

        val prefs = getSharedPreferences("blueberry", MODE_PRIVATE)
        // Restore collapsed categories
        prefs.getStringSet("clickgui_collapsed", emptySet())?.forEach { name ->
            runCatching { ModuleCategory.valueOf(name) }.getOrNull()?.let { collapsed.add(it) }
        }

        adapter = ModuleListAdapter(
            onToggle = { module, enabled ->
                if (module.isEnabled != enabled) {
                    ModuleRegistry.toggle(module.id)
                }
            },
            onCategoryClick = { category ->
                if (collapsed.contains(category)) collapsed.remove(category)
                else collapsed.add(category)
                prefs.edit()
                    .putStringSet("clickgui_collapsed", collapsed.map { it.name }.toSet())
                    .apply()
                refreshList()
            }
        )

        findViewById<RecyclerView>(R.id.moduleList).apply {
            layoutManager = LinearLayoutManager(this@ClickGuiActivity)
            adapter = laura.c@example.net
        }

        findViewById<EditText>(R.id.moduleSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterQuery = s?.toString()?.trim().orEmpty()
                refreshList()
            }
        })

        refreshList()
    }

    private fun refreshList() {
        val modules = ModuleRegistry.all()
            .filter {
                filterQuery.isEmpty() ||
                    it.displayName.contains(filterQuery, ignoreCase = true) ||
                    it.id.contains(filterQuery, ignoreCase = true) ||
                    it.category.name.contains(filterQuery, ignoreCase = true)
            }
            .groupBy { it.category }

        val items = mutableListOf<ListItem>()
        ModuleCategory.entries.forEach { category ->
            val group = modules[category] ?: return@forEach
            val isCollapsed = collapsed.contains(category) && filterQuery.isEmpty()
            items.add(ListItem.Header(category, group.size, isCollapsed))
            if (!isCollapsed) {
                group.forEach { items.add(ListItem.ModuleRow(it)) }
            }
        }
        adapter.submit(items)
    }

    sealed class ListItem {
        data class Header(
            val category: ModuleCategory,
            val count: Int,
            val collapsed: Boolean
        ) : ListItem()

        data class ModuleRow(val module: IModule) : ListItem()
    }

    class ModuleListAdapter(
        private val onToggle: (IModule, Boolean) -> Unit,
        private val onCategoryClick: (ModuleCategory) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<ListItem>()

        fun submit(newItems: List<ListItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int) = when (items[position]) {
            is ListItem.Header -> 0
            is ListItem.ModuleRow -> 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                HeaderVH(inflater.inflate(R.layout.item_category_header, parent, false))
            } else {
                ModuleVH(inflater.inflate(R.layout.item_module, parent, false))
            }
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is ListItem.Header -> (holder as HeaderVH).bind(item, onCategoryClick)
                is ListItem.ModuleRow -> (holder as ModuleVH).bind(item.module, onToggle)
            }
        }

        class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
            private val title: TextView = view.findViewById(R.id.categoryTitle)
            private val arrow: TextView = view.findViewById(R.id.categoryArrow)

            fun bind(item: ListItem.Header, onClick: (ModuleCategory) -> Unit) {
                title.text = "${item.category.name} (${item.count})"
                arrow.text = if (item.collapsed) "▶" else "▼"
                itemView.setOnClickListener { onClick(item.category) }
            }
        }

        class ModuleVH(view: View) : RecyclerView.ViewHolder(view) {
            private val name: TextView = view.findViewById(R.id.moduleName)
            private val desc: TextView = view.findViewById(R.id.moduleDesc)
            private val toggle: SwitchCompat = view.findViewById(R.id.moduleToggle)

            fun bind(module: IModule, onToggle: (IModule, Boolean) -> Unit) {
                name.text = module.displayName
                desc.text = module.description.ifEmpty { module.id }
                toggle.setOnCheckedChangeListener(null)
                toggle.isChecked = module.isEnabled
                toggle.setOnCheckedChangeListener { _, checked ->
                    onToggle(module, checked)
                }
            }
        }
    }
}
