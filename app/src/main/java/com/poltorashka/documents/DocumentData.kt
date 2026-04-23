package com.poltorashka.documents

data class DocumentData(
    val id: String,
    val type: String,
    val lastName: String,
    val firstName: String,
    val middleName: String,
    val seriesAndNumber: String,
    val issuedBy: String,
    val issueDate: String,
    val divisionCode: String,
    val birthDate: String,
    val birthPlace: String,
    val gender: String,
) {
    // Эта функция формирует длинную строку для отправки
    fun toShareString(): String {
        return """
            Тип: $type
            Фамилия: $lastName
            Имя: $firstName
            Отчество: $middleName
            Серия и номер: $seriesAndNumber
            Паспорт выдан: $issuedBy
            Дата выдачи: $issueDate
            Код подразделения: $divisionCode
            Дата рождения: $birthDate
            Место рождения: $birthPlace
            Пол: $gender
        """.trimIndent()
    }
}
