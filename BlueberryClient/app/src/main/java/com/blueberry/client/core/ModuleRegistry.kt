package com.blueberry.client.core

/**
 * Singleton yang nyimpen semua module yang aktif.
 */
object ModuleRegistry {
    private val modules = linkedMapOf<String, IModule>()
    private var context: ModuleContext? = null

    fun init(ctx: ModuleContext) {
        context = ctx
    }

    fun rebind(ctx: ModuleContext) {
        context = ctx
        modules.values.forEach { it.onLoad(ctx) }
    }

    fun register(module: IModule) {
        modules[module.id] = module
        context?.let { module.onLoad(it) }
    }

    fun get(id: String): IModule? = modules[id]

    fun all(): List<IModule> = modules.values.toList()

    fun byCategory(category: ModuleCategory): List<IModule> =
        modules.values.filter { it.category == category }

    fun toggle(id: String) {
        val module = modules[id] ?: return
        module.isEnabled = !module.isEnabled
        if (module.isEnabled) module.onEnable() else module.onDisable()
    }

    fun tickAll() {
        modules.values.filter { it.isEnabled }.forEach { it.onTick() }
    }
}
