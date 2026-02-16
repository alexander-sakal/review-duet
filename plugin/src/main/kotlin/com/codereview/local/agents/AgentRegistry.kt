package com.codereview.local.agents

object AgentRegistry {
    private val providers = mutableListOf<AgentProvider>()

    fun register(provider: AgentProvider) {
        providers.removeAll { it.id == provider.id }
        providers.add(provider)
    }

    fun getAll(): List<AgentProvider> = providers.toList()

    fun getById(id: String): AgentProvider? = providers.find { it.id == id }

    fun getDefault(): AgentProvider = providers.first()
}
