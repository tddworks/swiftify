package com.example

import io.swiftify.annotations.SwiftDefaults
import io.swiftify.annotations.SwiftFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/**
 * Simple Notes Repository - demonstrates Swiftify features
 *
 * Features shown:
 * - @SwiftDefaults: generates Swift convenience overloads for default parameters
 * - @SwiftFlow: Kotlin Flow → Swift AsyncStream
 */
class NotesRepository {

    private val _notes = MutableStateFlow<List<Note>>(sampleNotes())
    private var nextId = 100

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // StateFlow → AsyncStream property
    // Swift: for await notes in repository.notesStream { ... }
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @SwiftFlow
    val notes: StateFlow<List<Note>> = _notes

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Suspend function with default parameters
    // Swift generates convenience overloads:
    //   - getNotes()
    //   - getNotes(limit:)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @SwiftDefaults
    suspend fun getNotes(
        limit: Int = 10,
        includeArchived: Boolean = false
    ): List<Note> {
        delay(100)
        return _notes.value
            .filter { includeArchived || !it.archived }
            .take(limit)
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Simple suspend function
    // Swift: let note = try await repository.getNote(id: "1")
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @SwiftDefaults
    suspend fun getNote(id: String): Note? {
        delay(50)
        return _notes.value.find { it.id == id }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Suspend function with multiple default parameters
    // Swift generates overloads:
    //   - createNote(title:)
    //   - createNote(title:, content:)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @SwiftDefaults
    suspend fun createNote(
        title: String,
        content: String = "",
        pinned: Boolean = false
    ): Note {
        delay(100)
        val note = Note(
            id = "note_${nextId++}",
            title = title,
            content = content,
            pinned = pinned
        )
        _notes.value = listOf(note) + _notes.value
        return note
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Suspend function (no defaults)
    // Swift: try await repository.deleteNote(id: "1")
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @SwiftDefaults
    suspend fun deleteNote(id: String) {
        delay(50)
        _notes.value = _notes.value.filter { it.id != id }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Flow function → AsyncStream
    // Swift: for await note in repository.watchNote(id: "1") { ... }
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @SwiftFlow
    fun watchNote(id: String): Flow<Note?> = flow {
        while (true) {
            emit(_notes.value.find { it.id == id })
            delay(1000)
        }
    }

    private fun sampleNotes() = listOf(
        Note("1", "Welcome", "Welcome to Swiftify!", pinned = true),
        Note("2", "Shopping List", "Milk, Eggs, Bread"),
        Note("3", "Ideas", "Build something awesome")
    )
}

data class Note(
    val id: String,
    val title: String,
    val content: String = "",
    val pinned: Boolean = false,
    val archived: Boolean = false
)
