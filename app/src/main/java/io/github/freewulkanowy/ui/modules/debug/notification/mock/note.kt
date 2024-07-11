package io.github.freewulkanowy.ui.modules.debug.notification.mock

import io.github.freewulkanowy.data.db.entities.Note
import io.github.freewulkanowy.sdk.scrapper.notes.NoteCategory
import java.time.LocalDate

val debugNoteItems = listOf(
    generateNote("Aleksadra Krajewska", "Przeszkadzanie na lekcjach", NoteCategory.NEGATIVE),
    generateNote("Zofia Czerwińska", "Udział w konkursie szkolnym", NoteCategory.POSITIVE),
    generateNote("Stanisław Krupa", "Kultura języka", NoteCategory.NEUTRAL),
    generateNote("Karolina Kowalska", "Wypełnianie obowiązków ucznia", NoteCategory.NEUTRAL),
    generateNote("Joanna Krupa", "Umycie tablicy cifem", NoteCategory.POSITIVE),
    generateNote("Duchowicz Maksymilian", "Reprezentowanie szkoły", NoteCategory.POSITIVE),
    generateNote("Michał Mazur", "Przeszkadzanie na lekcji", NoteCategory.NEGATIVE),
    generateNote("Karolina Kowalska", "Wypełnianie obowiązków ucznia", NoteCategory.NEGATIVE),
    generateNote("Aleksandra Krajewska", "Wysadzenie klasy w powietrze", NoteCategory.NEGATIVE),
)

private fun generateNote(teacher: String, category: String, type: NoteCategory) = Note(
    teacher = teacher,
    category = category,
    categoryType = type.id,
    studentId = 0,
    date = LocalDate.now(),
    teacherSymbol = "",
    isPointsShow = false,
    points = 0,
    content = ""
)
