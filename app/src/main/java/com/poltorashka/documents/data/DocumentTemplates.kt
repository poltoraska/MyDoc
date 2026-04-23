package com.poltorashka.documents.data

object DocumentTemplates {
    val passportFields = listOf("Фамилия", "Имя", "Отчество", "Серия и номер", "Кем выдан", "Дата выдачи", "Код подразделения")
    val snilsFields = listOf("ФИО", "Номер СНИЛС")
    val autoLicenseFields = listOf("ФИО", "Категории", "Серия и номер", "Действительно до")
    // ... здесь потом допишешь остальные (ОМС, ИНН и т.д.)

    // Функция, которая отдает нужный список полей по названию документа
    fun getFieldsForType(type: String): List<String> {
        return when (type) {
            "Паспорт РФ", "Заграничный паспорт" -> passportFields
            "СНИЛС" -> snilsFields
            "Водительское удостоверение" -> autoLicenseFields
            else -> listOf("Данные")
        }
    }
}