package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.MobileDevice
import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.data.pojos.MobileDeviceToken
import io.github.freewulkanowy.sdk.pojo.Device as SdkDevice
import io.github.freewulkanowy.sdk.pojo.Token as SdkToken

fun List<SdkDevice>.mapToEntities(student: Student) = map {
    MobileDevice(
        studentId = student.studentId,
        date = it.createDate.toInstant(),
        deviceId = it.id,
        name = it.name
    )
}

fun SdkToken.mapToMobileDeviceToken() = MobileDeviceToken(
    token = token,
    symbol = symbol,
    pin = pin,
    qr = qrCodeImage
)
