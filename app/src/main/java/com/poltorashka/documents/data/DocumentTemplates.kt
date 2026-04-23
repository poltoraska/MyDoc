package com.poltorashka.documents.data

object DocumentTemplates {

    val passportFields = listOf(
        "Фамилия", "Имя", "Отчество", "Серия и номер", "Паспорт выдан",
        "Дата выдачи", "Код подразделения", "Дата рождения", "Место рождения", "Пол"
    )

    val foreignPassportFields = listOf(
        "Фамилия", "Имя", "Отчество", "Фамилия (латиницей)", "Имя (латиницей)",
        "Номер", "Дата выдачи", "Дата окончания срока действия", "Орган, выдавший документ"
    )

    val innFields = listOf(
        "ФИО", "Номер"
    )

    val snilsFields = listOf(
        "ФИО", "Номер"
    )

    val driverLicenseFields = listOf(
        "Фамилия", "Имя", "Отчество", "Номер", "Дата выдачи", "Дата рождения", "Место рождения"
    )

    val omsFields = listOf(
        "ФИО", "Номер"
    )

    val birthCertificateFields = listOf(
        "Дата рождения", "Место рождения", "Дата записи акта о рождении",
        "Составлена запись акта о рождении №", "Место регистрации документа",
        "Дата выдачи документа", "Серия документа", "Номер документа"
    )

    // Полный список документов для выпадающего меню при создании
    val supportedDocumentTypes = listOf(
        "Паспорт РФ",
        "Заграничный паспорт",
        "ИНН",
        "СНИЛС",
        "Водительское удостоверение",
        "Полис ОМС",
        "Свидетельство о рождении"
    )

    // Функция, которая отдает нужный список полей по названию документа
    fun getFieldsForType(type: String): List<String> {
        return when (type) {
            "Паспорт РФ" -> passportFields
            "Заграничный паспорт" -> foreignPassportFields
            "ИНН" -> innFields
            "СНИЛС" -> snilsFields
            "Водительское удостоверение" -> driverLicenseFields
            "Полис ОМС" -> omsFields
            "Свидетельство о рождении" -> birthCertificateFields
            else -> listOf("Данные")
        }
    }
}