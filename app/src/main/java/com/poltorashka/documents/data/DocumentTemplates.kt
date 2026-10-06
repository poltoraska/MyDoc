package com.poltorashka.documents.data

import android.content.res.Resources
import androidx.annotation.StringRes
import com.poltorashka.documents.R

data class DocumentTemplateField(
    val storageKey: String,
    @StringRes val labelRes: Int
)

data class DocumentTypeTemplate(
    val storageValue: String,
    @StringRes val labelRes: Int,
    val fields: List<DocumentTemplateField>
)

object DocumentTemplates {
    const val tagFieldKey = "Тег"

    private fun field(storageKey: String, @StringRes labelRes: Int) =
        DocumentTemplateField(storageKey, labelRes)

    val supportedDocumentTypes = listOf(
        DocumentTypeTemplate("Паспорт РФ", R.string.document_type_passport_rf, listOf(
            field("Фамилия", R.string.field_last_name), field("Имя", R.string.field_first_name),
            field("Отчество", R.string.field_middle_name), field("Серия и номер", R.string.field_series_and_number),
            field("Паспорт выдан", R.string.field_passport_issued_by), field("Дата выдачи", R.string.field_issue_date),
            field("Код подразделения", R.string.field_department_code), field("Дата рождения", R.string.field_birth_date),
            field("Место рождения", R.string.field_place_of_birth), field("Пол", R.string.field_gender),
            field("Прописка", R.string.field_registration_address)
        )),
        DocumentTypeTemplate("Заграничный паспорт", R.string.document_type_foreign_passport, listOf(
            field("Фамилия", R.string.field_last_name), field("Имя", R.string.field_first_name),
            field("Отчество", R.string.field_middle_name), field("Фамилия (латиницей)", R.string.field_last_name_latin),
            field("Имя (латиницей)", R.string.field_first_name_latin), field("Номер", R.string.field_number),
            field("Дата выдачи", R.string.field_issue_date), field("Дата окончания срока действия", R.string.field_expiration_date),
            field("Орган, выдавший документ", R.string.field_issuing_authority)
        )),
        DocumentTypeTemplate("ИНН", R.string.document_type_inn, listOf(
            field("ФИО", R.string.field_full_name), field("Номер", R.string.field_number)
        )),
        DocumentTypeTemplate("СНИЛС", R.string.document_type_snils, listOf(
            field("ФИО", R.string.field_full_name), field("Номер", R.string.field_number)
        )),
        DocumentTypeTemplate("Водительское удостоверение", R.string.document_type_driver_license, listOf(
            field("Фамилия", R.string.field_last_name), field("Имя", R.string.field_first_name),
            field("Отчество", R.string.field_middle_name), field("Номер", R.string.field_number),
            field("Дата выдачи", R.string.field_issue_date), field("Дата рождения", R.string.field_birth_date),
            field("Место рождения", R.string.field_place_of_birth)
        )),
        DocumentTypeTemplate("Полис ОМС", R.string.document_type_health_insurance, listOf(
            field("ФИО", R.string.field_full_name), field("Номер", R.string.field_number)
        )),
        DocumentTypeTemplate("Свидетельство о рождении", R.string.document_type_birth_certificate, listOf(
            field("ФИО", R.string.field_full_name), field("Дата рождения", R.string.field_birth_date),
            field("Место рождения", R.string.field_place_of_birth), field("Дата записи акта о рождении", R.string.field_birth_record_date),
            field("Составлена запись акта о рождении №", R.string.field_birth_record_number),
            field("Место регистрации документа", R.string.field_registration_place),
            field("Дата выдачи документа", R.string.field_document_issue_date), field("Серия документа", R.string.field_document_series),
            field("Номер документа", R.string.field_document_number), field("Номер записи", R.string.field_record_number)
        )),
        DocumentTypeTemplate("Свидетельство о заключении брака", R.string.document_type_marriage_certificate, listOf(
            field("Дата заключения", R.string.field_marriage_date), field("Дата выдачи", R.string.field_issue_date),
            field("Кем выдан", R.string.field_issued_by), field("Номер записи", R.string.field_record_number)
        )),
        DocumentTypeTemplate("Выписка из ЕГРН", R.string.document_type_property_extract, listOf(
            field("ФИО", R.string.field_full_name), field("Дата присвоения кадастрого номера", R.string.field_cadastral_number_date),
            field("Местоположение", R.string.field_location), field("Наименование", R.string.field_name),
            field("Кадастровые номера иных объектов в пределах объекта недвижимости", R.string.field_other_cadastral_numbers)
        )),
        DocumentTypeTemplate("Свидетельство о регистрации ТС", R.string.document_type_vehicle_registration, listOf(
            field("Серия и номер", R.string.field_series_and_number), field("ФИО", R.string.field_full_name),
            field("Дата выдачи", R.string.field_issue_date), field("Регистрационный знак", R.string.field_registration_plate),
            field("Идентификационный номер (VIN)", R.string.field_vin), field("Марка, модель", R.string.field_make_model),
            field("Год выпуска ТС", R.string.field_vehicle_year)
        )),
        DocumentTypeTemplate("Другое", R.string.document_type_other, listOf(
            field("Название документа", R.string.field_document_title), field("ФИО", R.string.field_full_name),
            field("Номер документа", R.string.field_document_number), field("Дата выдачи", R.string.field_issue_date),
            field("Комментарий", R.string.field_comment)
        ))
    )

    fun getTemplate(type: String): DocumentTypeTemplate? =
        supportedDocumentTypes.firstOrNull { it.storageValue == type }

    fun getFieldsForType(type: String): List<DocumentTemplateField> =
        getTemplate(type)?.fields ?: listOf(field("Данные", R.string.field_data))

    @StringRes
    fun documentTypeLabelRes(type: String): Int? = getTemplate(type)?.labelRes

    fun documentTypeLabel(type: String, resources: Resources): String =
        documentTypeLabelRes(type)?.let(resources::getString) ?: type

    @StringRes
    fun fieldLabelRes(storageKey: String): Int? = supportedDocumentTypes
        .asSequence()
        .flatMap { it.fields.asSequence() }
        .firstOrNull { it.storageKey == storageKey }
        ?.labelRes

    fun fieldLabel(storageKey: String, resources: Resources): String =
        fieldLabelRes(storageKey)?.let(resources::getString) ?: storageKey

    fun isDateField(storageKey: String): Boolean = storageKey in dateFieldKeys

    private val dateFieldKeys = setOf(
        "Дата выдачи", "Дата рождения", "Дата окончания срока действия", "Дата записи акта о рождении",
        "Дата выдачи документа", "Дата заключения", "Дата присвоения кадастрого номера"
    )
}
